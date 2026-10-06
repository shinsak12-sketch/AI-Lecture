package com.bosang.search.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecordingNameTest {
    @Test fun 저장안된번호() {
        val r = RecordingName.parse("통화 녹음 01011112222_261002_174946.m4a")!!
        assertTrue(r.isNumber)
        assertEquals("01011112222", r.number)
        assertEquals(LocalDateTime.of(2026, 10, 2, 17, 49, 46), r.time)
    }

    @Test fun 저장된연락처() {
        val r = RecordingName.parse("통화 녹음 홍길동_261002_174946.m4a")!!
        assertFalse(r.isNumber)
        assertEquals("홍길동", r.label)
        assertEquals(LocalDateTime.of(2026, 10, 2, 17, 49, 46), r.time)
    }

    @Test fun 확장자없음() {
        val r = RecordingName.parse("통화 녹음 홍길동_261002_174946")!!
        assertEquals("홍길동", r.label)
    }

    @Test fun 이름에_밑줄과_공백과_점() {
        assertEquals("홍길동_보험 담당", RecordingName.parse("통화 녹음 홍길동_보험 담당_261002_174946.m4a")!!.label)
        assertEquals("Dr.Kim", RecordingName.parse("통화 녹음 Dr.Kim_261002_174946")!!.label)
    }

    @Test fun 중복꼬리() {
        assertEquals("홍길동", RecordingName.parse("통화 녹음 홍길동_261002_174946(1).m4a")!!.label)
    }

    @Test fun 통화녹음이_아닌_파일() {
        assertNull(RecordingName.parse("음성 메모 001.m4a"))
        assertNull(RecordingName.parse("통화 녹음 홍길동.m4a"))
        assertNull(RecordingName.parse("통화 녹음 홍길동_261302_174946.m4a")) // 13월
    }
}

class PhoneNumbersTest {
    @Test fun 표기통일() {
        assertEquals("01011112222", PhoneNumbers.normalize("010-1111-2222"))
        assertEquals("01011112222", PhoneNumbers.normalize("+82 10-1111-2222"))
        assertEquals("01011112222", PhoneNumbers.normalize("+82 010 1111 2222"))
        assertEquals("01011112222", PhoneNumbers.normalize("0082-10-1111-2222"))
        assertEquals("0212345678", PhoneNumbers.normalize("02-1234-5678"))
        assertEquals("", PhoneNumbers.normalize(null))
    }

    @Test fun 보기좋게() {
        assertEquals("010-1111-2222", PhoneNumbers.format("01011112222"))
        assertEquals("02-1234-5678", PhoneNumbers.format("0212345678"))
        assertEquals("02-123-4567", PhoneNumbers.format("021234567"))
        assertEquals("031-123-4567", PhoneNumbers.format("0311234567"))
        assertEquals("1588-1234", PhoneNumbers.format("15881234"))
    }

    @Test fun 번호인지_이름인지() {
        assertTrue(PhoneNumbers.looksLikeNumber("01011112222"))
        assertTrue(PhoneNumbers.looksLikeNumber("+82 10-1111-2222"))
        assertFalse(PhoneNumbers.looksLikeNumber("홍길동"))
        assertFalse(PhoneNumbers.looksLikeNumber("김철수 010"))
        assertFalse(PhoneNumbers.looksLikeNumber("112"))
    }
}

class RecordingMatcherTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private fun ms(h: Int, m: Int, s: Int) =
        LocalDateTime.of(2026, 10, 2, h, m, s).atZone(zone).toInstant().toEpochMilli()

    private fun rec(name: String) = RecordingName.parse(name)!!
    private fun recMs(name: String) = rec(name).time.atZone(zone).toInstant().toEpochMilli()

    private val noContacts: (String) -> List<String> = { emptyList() }

    @Test fun 번호파일_통화기록_확인() {
        val f = "통화 녹음 01011112222_261002_174946"
        val calls = listOf(CallEntry("01011112222", null, ms(17, 49, 30), 120, 2))
        val m = RecordingMatcher.match(rec(f), recMs(f), calls, noContacts)!!
        assertEquals(listOf("01011112222"), m.numbers)
        assertEquals(MatchMethod.CALL_LOG, m.method)
    }

    @Test fun 번호파일_통화기록_없음() {
        val f = "통화 녹음 01011112222_261002_174946"
        val m = RecordingMatcher.match(rec(f), recMs(f), emptyList(), noContacts)!!
        assertEquals(MatchMethod.FILE_NUMBER, m.method)
    }

    @Test fun 이름파일_그시각_통화기록의_이름() {
        val f = "통화 녹음 홍길동_261002_174946"
        val calls = listOf(
            CallEntry("01011112222", "홍길동", ms(17, 49, 10), 90, 1),
            CallEntry("01099998888", "김철수", ms(17, 48, 40), 10, 1),
        )
        val m = RecordingMatcher.match(rec(f), recMs(f), calls, noContacts)!!
        assertEquals(listOf("01011112222"), m.numbers)
        assertEquals(MatchMethod.CALL_LOG, m.method)
    }

    @Test fun 이름파일_연락처_이름이_바뀐경우_그시각통화하나() {
        val f = "통화 녹음 홍길동_261002_174946"
        val calls = listOf(CallEntry("01011112222", "홍길동 부장", ms(17, 49, 0), 90, 2))
        val m = RecordingMatcher.match(rec(f), recMs(f), calls, noContacts)!!
        assertEquals(listOf("01011112222"), m.numbers)
        assertEquals(MatchMethod.CALL_LOG, m.method)
    }

    @Test fun 이름파일_통화기록_지워짐_연락처로() {
        val f = "통화 녹음 홍길동_261002_174946"
        val m = RecordingMatcher.match(rec(f), recMs(f), emptyList()) { listOf("010-1111-2222") }!!
        assertEquals(listOf("01011112222"), m.numbers)
        assertEquals(MatchMethod.CONTACT_NAME, m.method)
    }

    @Test fun 동명이인은_확인필요() {
        val f = "통화 녹음 홍길동_261002_174946"
        val m = RecordingMatcher.match(rec(f), recMs(f), emptyList()) { listOf("01011112222", "01033334444") }!!
        assertEquals(MatchMethod.AMBIGUOUS, m.method)
        assertEquals(2, m.numbers.size)
    }

    @Test fun 동명이인이라도_그시각_통화한_번호로_확정() {
        val f = "통화 녹음 홍길동_261002_174946"
        val calls = listOf(CallEntry("01033334444", null, ms(17, 49, 20), 60, 2))
        val m = RecordingMatcher.match(rec(f), recMs(f), calls) { listOf("01011112222", "01033334444") }!!
        assertEquals(listOf("01033334444"), m.numbers)
        assertEquals(MatchMethod.CALL_LOG, m.method)
    }

    @Test fun 시간이_멀면_무시() {
        val f = "통화 녹음 홍길동_261002_174946"
        val calls = listOf(CallEntry("01011112222", "홍길동", ms(16, 0, 0), 60, 1))
        assertNull(RecordingMatcher.match(rec(f), recMs(f), calls, noContacts))
    }
}

class CaseNumberTest {
    @Test fun 형식() {
        assertTrue(CaseNumber.isValid("26-00012345"))
        assertFalse(CaseNumber.isValid("26-0001234"))
        assertFalse(CaseNumber.isValid("2026-00012345"))
        assertEquals("26", CaseNumber.year2(2026))
        assertEquals("05", CaseNumber.year2(2005))
    }

    @Test fun 검색() {
        assertTrue(CaseNumber.matches("26-00012345", "12345"))
        assertTrue(CaseNumber.matches("26-00012345", "26-0001"))
        assertFalse(CaseNumber.matches("26-00012345", "99999"))
        assertFalse(CaseNumber.matches("26-00012345", "홍길동"))
    }
}
