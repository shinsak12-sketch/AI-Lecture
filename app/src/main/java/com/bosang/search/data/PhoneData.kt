package com.bosang.search.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import com.bosang.search.core.CallEntry
import com.bosang.search.core.ParsedRecording
import com.bosang.search.core.PhoneNumbers
import com.bosang.search.core.RecordingName
import java.text.Collator
import java.time.ZoneId
import java.util.Locale

data class SmsItem(
    /** 일반 문자는 양수, MMS(장문·사진)는 음수 */
    val id: Long,
    val number: String,
    val body: String,
    val timeMillis: Long,
    val incoming: Boolean,
    /** MMS 사진 (content://mms/part/…) */
    val images: List<Uri> = emptyList(),
)

data class ContactEntry(
    val name: String,
    val number: String,
    /** 휴대전화, 회사 같은 번호 종류 */
    val label: String?,
)

data class RecordingFile(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val parsed: ParsedRecording,
    val timeMillis: Long,
) {
    /** 같은 ID가 다른 파일에 재사용되는 경우를 막으려고 이름까지 묶어서 기억 */
    val cacheKey: String get() = "$id|$displayName"
}

/** 검색용: 연락처 + 통화내역의 번호 하나 */
data class DirEntry(
    val number: String,
    val name: String?,
    /** 휴대전화, 회사 같은 번호 종류 */
    val label: String?,
    /** 마지막 통화 시각 (통화내역에 없으면 null) */
    val lastCall: Long?,
)

/** 폰 안의 통화기록·연락처·문자·통화녹음을 읽기만 한다. */
class PhoneData(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val res = context.applicationContext.resources

    /** 최신순 통화기록 */
    fun calls(limit: Int): List<CallEntry> {
        val out = ArrayList<CallEntry>()
        val proj = arrayOf(
            CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE,
            CallLog.Calls.DURATION, CallLog.Calls.TYPE,
        )
        runCatching {
            resolver.query(CallLog.Calls.CONTENT_URI, proj, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    out.add(
                        CallEntry(
                            number = PhoneNumbers.normalize(c.getString(0)),
                            name = c.getString(1),
                            timeMillis = c.getLong(2),
                            durationSec = c.getLong(3),
                            type = c.getInt(4),
                        ),
                    )
                }
            }
        }
        return out
    }

    fun contactName(number: String): String? {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            resolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    fun numbersForName(name: String): List<String> {
        val out = ArrayList<String>()
        runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
                arrayOf(name),
                null,
            )?.use { c -> while (c.moveToNext()) out.add(PhoneNumbers.normalize(c.getString(0))) }
        }
        return out.filter { it.isNotEmpty() }.distinct()
    }

    /** 연락처 이름 → 번호들 (녹음 파일 이름으로 번호 찾을 때 한 번에 읽어 둠) */
    fun contactNumbersByName(): Map<String, List<String>> {
        val out = HashMap<String, MutableList<String>>()
        runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = PhoneNumbers.normalize(c.getString(1))
                    if (number.isEmpty()) continue
                    val list = out.getOrPut(name) { ArrayList() }
                    if (number !in list) list.add(number)
                }
            }
        }
        return out
    }

    /** 번호가 있는 연락처 전부 (한 사람이 번호가 여러 개면 번호마다 한 줄), 이름순 */
    fun contacts(): List<ContactEntry> {
        val out = ArrayList<ContactEntry>()
        runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                ),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = PhoneNumbers.normalize(c.getString(1))
                    if (number.isEmpty()) continue
                    val name = c.getString(0)?.trim().orEmpty()
                    val label = runCatching {
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(res, c.getInt(2), c.getString(3)).toString()
                    }.getOrNull()
                    out.add(ContactEntry(name.ifEmpty { PhoneNumbers.format(number) }, number, label))
                }
            }
        }
        val collator = Collator.getInstance(Locale.KOREAN)
        return out.distinctBy { it.name + "|" + it.number }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    /** 연락처와 최근 통화내역을 번호 하나당 한 줄로 (최근 통화한 번호가 앞) */
    fun directory(): List<DirEntry> {
        val byNumber = LinkedHashMap<String, DirEntry>()
        calls(2000).forEach { c ->
            if (c.number.isNotEmpty() && c.number !in byNumber) {
                byNumber[c.number] = DirEntry(c.number, c.name?.takeIf { it.isNotBlank() }, null, c.timeMillis)
            }
        }
        contacts().forEach { p ->
            val prev = byNumber[p.number]
            byNumber[p.number] = DirEntry(p.number, p.name, p.label, prev?.lastCall)
        }
        return byNumber.values.toList()
    }

    /** 그 번호와 주고받은 문자 (표기 차이는 무시하고 비교) */
    fun sms(number: String): List<SmsItem> {
        val n = PhoneNumbers.normalize(number)
        if (n.length < 3) return emptyList()
        val out = ArrayList<SmsItem>()
        runCatching {
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                "${Telephony.Sms.ADDRESS} LIKE ?",
                arrayOf("%" + n.takeLast(4)),
                "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    if (PhoneNumbers.normalize(c.getString(1)) != n) continue
                    out.add(
                        SmsItem(
                            id = c.getLong(0),
                            number = n,
                            body = c.getString(2).orEmpty(),
                            timeMillis = c.getLong(3),
                            incoming = c.getInt(4) == Telephony.Sms.MESSAGE_TYPE_INBOX,
                        ),
                    )
                }
            }
        }
        return out
    }

    /** 그 번호와 주고받은 MMS (장문 문자 · 사진). 1:1 대화방만 */
    fun mms(number: String): List<SmsItem> {
        val n = PhoneNumbers.normalize(number)
        if (n.length < 3) return emptyList()
        val threads = threadIds(n)
        if (threads.isEmpty()) return emptyList()
        val out = ArrayList<SmsItem>()
        runCatching {
            resolver.query(
                Uri.parse("content://mms"),
                arrayOf("_id", "date", "msg_box"),
                "thread_id IN (${threads.joinToString(",")})",
                null,
                "date DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val box = c.getInt(2)
                    if (box != 1 && box != 2) continue // 받은 것 · 보낸 것만
                    val (text, images) = mmsParts(id)
                    if (text.isBlank() && images.isEmpty()) continue
                    out.add(SmsItem(-id, n, text, c.getLong(1) * 1000, box == 1, images))
                }
            }
        }
        return out
    }

    private fun mmsParts(mmsId: Long): Pair<String, List<Uri>> {
        val texts = ArrayList<String>()
        val images = ArrayList<Uri>()
        runCatching {
            resolver.query(Uri.parse("content://mms/part"), arrayOf("_id", "ct", "text"), "mid = ?", arrayOf(mmsId.toString()), null)
                ?.use { c ->
                    while (c.moveToNext()) {
                        val partId = c.getLong(0)
                        val ct = c.getString(1).orEmpty()
                        when {
                            ct == "text/plain" -> texts.add(c.getString(2) ?: readPartText(partId))
                            ct.startsWith("image/") -> images.add(Uri.parse("content://mms/part/$partId"))
                        }
                    }
                }
        }
        return texts.joinToString("\n").trim() to images
    }

    private fun readPartText(partId: Long): String = runCatching {
        resolver.openInputStream(Uri.parse("content://mms/part/$partId"))?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull().orEmpty()

    /** 번호 하나와의 1:1 대화방 번호들 (문자 기록 + 대화방 주소록 두 군데서) */
    private fun threadIds(n: String): Set<Long> {
        val ids = HashSet<Long>()
        runCatching {
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.ADDRESS} LIKE ?",
                arrayOf("%" + n.takeLast(4)),
                null,
            )?.use { c -> while (c.moveToNext()) if (PhoneNumbers.normalize(c.getString(0)) == n) ids.add(c.getLong(1)) }
        }
        runCatching {
            val addrIds = HashSet<String>()
            resolver.query(Uri.parse("content://mms-sms/canonical-addresses"), arrayOf("_id", "address"), null, null, null)
                ?.use { c -> while (c.moveToNext()) if (PhoneNumbers.normalize(c.getString(1)) == n) addrIds.add(c.getLong(0).toString()) }
            if (addrIds.isNotEmpty()) {
                resolver.query(Uri.parse("content://mms-sms/conversations?simple=true"), arrayOf("_id", "recipient_ids"), null, null, null)
                    ?.use { c ->
                        while (c.moveToNext()) {
                            val r = c.getString(1)?.trim().orEmpty()
                            if (r in addrIds) ids.add(c.getLong(0))
                        }
                    }
            }
        }
        return ids
    }

    /** "통화 녹음 …" 으로 시작하는 오디오 파일 전부 */
    fun recordings(): List<RecordingFile> {
        val out = ArrayList<RecordingFile>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val zone = ZoneId.systemDefault()
        runCatching {
            resolver.query(
                base,
                arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION),
                "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("통화%"),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    val parsed = RecordingName.parse(name) ?: continue
                    val id = c.getLong(0)
                    out.add(
                        RecordingFile(
                            id = id,
                            uri = ContentUris.withAppendedId(base, id),
                            displayName = name,
                            durationMs = c.getLong(2),
                            parsed = parsed,
                            timeMillis = parsed.time.atZone(zone).toInstant().toEpochMilli(),
                        ),
                    )
                }
            }
        }
        return out
    }
}
