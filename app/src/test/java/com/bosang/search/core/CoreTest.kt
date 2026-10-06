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

class HangulTest {
    @Test fun 초성() {
        assertEquals("ㅎㄱㄷ", Hangul.initials("홍길동"))
        assertEquals("ㄱㄴㅈㅂ", Hangul.initials("강남정비"))
    }

    @Test fun 이름검색() {
        assertTrue(Hangul.matches("홍길동", "길동"))
        assertTrue(Hangul.matches("홍길동", "ㅎㄱㄷ"))
        assertTrue(Hangul.matches("홍길동", "ㅎㄱ"))
        assertTrue(Hangul.matches("서울한방병원", "ㅎㅂ"))
        assertTrue(Hangul.matches("서울 한방병원", "ㅅㅇㅎㅂ"))
        assertTrue(Hangul.matches("Dr.Kim", "kim"))
        assertTrue(Hangul.matches("홍길동", ""))
        assertFalse(Hangul.matches("홍길동", "김"))
        assertFalse(Hangul.matches("홍길동", "ㄱㅎ"))
    }

    @Test fun 연락처색인() {
        assertEquals("ㄱ", Hangul.indexOf("강남정비"))
        assertEquals("ㄱ", Hangul.indexOf("김영희"))
        assertEquals("ㄷ", Hangul.indexOf("딸기농장"))
        assertEquals("ㅎ", Hangul.indexOf(" 홍길동"))
        assertEquals("A", Hangul.indexOf("Dr.Kim"))
        assertEquals("#", Hangul.indexOf("010-1234"))
        assertEquals("#", Hangul.indexOf(""))
    }
}

class MoneyTest {
    @Test fun 요약() {
        assertEquals("120만", Money.short(1_200_000))
        assertEquals("1,234,567원", Money.short(1_234_567))
        assertEquals("5,000원", Money.short(5_000))
        assertEquals("1,000만", Money.short(10_000_000))
    }

    @Test fun 입력() {
        assertEquals(1_240_000L, Money.parse("1,240,000"))
        assertNull(Money.parse(""))
        assertEquals("1,240,000", Money.comma(1_240_000))
    }
}

class SmsFilterTest {
    @Test fun 통화알림문자는_걸러냄() {
        assertTrue(SmsFilter.isCallNotice("[Web발신]\n[캐치콜] 010-1234-5678님이 10/06 14:20에 전화하셨습니다."))
        assertTrue(SmsFilter.isCallNotice("[매너콜] 010-1234-5678 고객님께서 전화를 거셨습니다."))
        assertTrue(SmsFilter.isCallNotice("콜키퍼 알림: 14:05 부재중 1건"))
        assertTrue(SmsFilter.isCallNotice("[통화가능알림] 지금 통화가 가능합니다"))
        assertTrue(SmsFilter.isCallNotice("홍길동님이 전화를 거셨습니다"))
        assertTrue(SmsFilter.isCallNotice("10월6일 14:20 전화하셨습니다"))
    }

    @Test fun 사람이_쓴_문자는_그대로() {
        assertFalse(SmsFilter.isCallNotice("견적서 사진 다시 보내드릴게요. 범퍼 쪽이 잘 안 나왔네요."))
        assertFalse(SmsFilter.isCallNotice("아까 전화 못 받아서요 2시 이후에 통화 가능할까요?"))
        assertFalse(SmsFilter.isCallNotice("14:30에 정비소 도착 예정입니다"))
        assertFalse(SmsFilter.isCallNotice(""))
    }
}


class RecordQueryTest {
    private val zone = java.time.ZoneId.of("Asia/Seoul")
    private val today = java.time.LocalDate.of(2026, 10, 6) // 화요일
    private fun at(m: Int, d: Int, h: Int = 10, mi: Int = 0, y: Int = 2026) =
        java.time.LocalDateTime.of(y, m, d, h, mi).atZone(zone).toInstant().toEpochMilli()
    private fun q(s: String) = RecordQuery.parse(s, today)

    @Test fun 날짜_여러_꼴() {
        assertTrue(q("10/3").matches(at(10, 3), "", zone))
        assertFalse(q("10/3").matches(at(10, 4), "", zone))
        assertTrue(q("10.3").matches(at(10, 3), "", zone))
        assertTrue(q("10월 3일").matches(at(10, 3), "", zone))
        assertTrue(q("10월").matches(at(10, 28), "", zone))
        assertFalse(q("10월").matches(at(9, 28), "", zone))
        assertTrue(q("3일").matches(at(9, 3), "", zone))
        assertTrue(q("2026.10.3").matches(at(10, 3), "", zone))
        assertFalse(q("2025.10.3").matches(at(10, 3), "", zone))
        assertTrue(q("10/1~10/5").matches(at(10, 4), "", zone))
        assertFalse(q("10/1~10/5").matches(at(10, 6), "", zone))
    }

    @Test fun 오늘_어제_이번주() {
        assertTrue(q("오늘").matches(at(10, 6), "", zone))
        assertTrue(q("어제").matches(at(10, 5), "", zone))
        assertFalse(q("어제").matches(at(10, 6), "", zone))
        assertTrue(q("이번주").matches(at(10, 5), "", zone))
        assertFalse(q("이번주").matches(at(10, 4), "", zone))
        assertTrue(q("지난주").matches(at(10, 1), "", zone))
        assertTrue(q("지난달").matches(at(9, 15), "", zone))
    }

    @Test fun 시각() {
        assertTrue(q("3시").matches(at(10, 6, 15, 20), "", zone))
        assertTrue(q("3시").matches(at(10, 6, 3, 20), "", zone))
        assertFalse(q("오후 3시").matches(at(10, 6, 3, 20), "", zone))
        assertTrue(q("14:30").matches(at(10, 6, 14, 30), "", zone))
        assertFalse(q("14:30").matches(at(10, 6, 14, 31), "", zone))
    }

    @Test fun 글자와_날짜_함께() {
        val text = "홍길동 피해자 01012345678 내일 정비소 견적서 보내드릴게요"
        assertTrue(q("견적").matches(at(10, 3), text, zone))
        assertTrue(q("10/3 견적").matches(at(10, 3), text, zone))
        assertFalse(q("10/4 견적").matches(at(10, 3), text, zone))
        assertFalse(q("견적 렌트").matches(at(10, 3), text, zone))
        assertTrue(q("ㅎㄱㄷ").matches(at(10, 3), text, zone))
        assertTrue(q("5678").matches(at(10, 3), text, zone))
        assertTrue(q("정비 소").matches(at(10, 3), text, zone))
        assertEquals(listOf("10월 3일"), q("10/3 견적").labels)
        assertEquals(listOf("견적"), q("10/3 견적").terms)
    }

    @Test fun 번호는_날짜로_안_읽음() {
        val p = q("010-1234-5678")
        assertTrue(p.labels.isEmpty())
        assertTrue(p.matches(at(10, 3), "01012345678 홍길동", zone))
        assertTrue(q("01012345678").matches(at(10, 3), "010-1234-5678 홍길동", zone))
        assertTrue(q("1234").labels.isEmpty())
    }
}

class ScheduleRulesTest {
    private val d = java.time.LocalDate.of(2026, 10, 1)

    @Test fun 입고_며칠째와_지연() {
        assertEquals(1, RepairRule.dayCount(d, d))
        assertEquals(6, RepairRule.dayCount(d, d.plusDays(5)))
        // 예정일 없이 기준 7일 → 7일째(10/7)까지는 괜찮고 10/8부터 지연
        assertEquals(0, RepairRule.overdueDays(d, null, 7, d.plusDays(6)))
        assertEquals(1, RepairRule.overdueDays(d, null, 7, d.plusDays(7)))
        // 예정일 10/5
        val out = java.time.LocalDate.of(2026, 10, 5)
        assertEquals(0, RepairRule.overdueDays(d, out, 7, out))
        assertEquals(3, RepairRule.overdueDays(d, out, 7, out.plusDays(3)))
    }

    @Test fun 상태_글자() {
        val out = java.time.LocalDate.of(2026, 10, 5)
        assertEquals("입고 3일째 · 출고 D-2", RepairRule.label(d, out, 7, null, d.plusDays(2)))
        assertEquals("입고 5일째 · 오늘 출고 예정", RepairRule.label(d, out, 7, null, out))
        assertEquals("입고 8일째 · 예정 3일 지남", RepairRule.label(d, out, 7, null, out.plusDays(3)))
        assertEquals("출고 완료 (6일)", RepairRule.label(d, out, 7, d.plusDays(5), d.plusDays(9)))
    }

    @Test fun 알림_시각() {
        val at = 10_000_000L * 60_000L
        val now = at - 45 * 60_000L
        val t = ReminderRule.times(at, listOf(1440, 60, 30, 0), now)
        assertEquals(listOf(30, 0), t.map { it.first })
        assertEquals("1일 전", ReminderRule.label(1440))
        assertEquals("1시간 전", ReminderRule.label(60))
        assertEquals("정시", ReminderRule.label(0))
    }
}
