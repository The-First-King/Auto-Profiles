package com.mine.autoprofiles.utils;

import com.mine.autoprofiles.models.Schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public final class ScheduleCalculator {

    private static final int MAX_ITERATIONS = 300_000;

    private ScheduleCalculator() {}

    public static long[] windows(Schedule s, long now) {
        return windows(s, now, ZoneId.systemDefault());
    }

    public static long[] windows(Schedule s, long now, ZoneId zone) {
        long[] result = {-1, -1, -1, -1};
        if (s == null) return result;

        if (s.mode == Schedule.Mode.ONCE) {
            long st = toMillis(s.onceStart.toLocalDate(), s.onceStart.toLocalTime(), zone);
            long en = toMillis(s.onceEnd.toLocalDate(), s.onceEnd.toLocalTime(), zone);
            if (en <= st) return result; // malformed
            if (st <= now && now < en) { result[0] = st; result[1] = en; }
            if (st > now) result[2] = st;
            if (en > now) result[3] = en;
            return result;
        }

        // ----- RECUR -----
        long durationMs = durationMs(s);
        int yielded = 0;      // occurrences produced so far (COUNT accounting)
        int iterations = 0;

        long curStart = -1, curEnd = -1, nextStart = -1;

        outer:
        for (int block = 0; ; block++) {
            if (++iterations > MAX_ITERATIONS) break;

            // Candidate date(s) for this block, chronological within the block
            LocalDate[] candidates = candidatesForBlock(s, block);
            if (candidates == null) break; // beyond hard horizon

            for (LocalDate d : candidates) {
                if (++iterations > MAX_ITERATIONS) break outer;
                if (d == null) continue;
                if (d.isBefore(s.anchor)) continue;

                // Termination checks (valid occurrences only)
                if (s.term == Schedule.Term.UNTIL && d.isAfter(s.until)) break outer;
                if (s.term == Schedule.Term.COUNT && yielded >= s.count) break outer;
                yielded++;

                long st = toMillis(d, s.startTime, zone);
                long en = st + durationMs;

                if (st <= now) {
                    if (now < en) { curStart = st; curEnd = en; }
                } else {
                    nextStart = st;
                    break outer; // everything later is irrelevant
                }
            }

            LocalDate probe = blockAnchorDate(s, block);
            if (probe != null && probe.getYear() > LocalDate.now(zone).getYear() + 200) break;
        }

        result[0] = curStart;
        result[1] = curEnd;
        result[2] = nextStart;
        if (curEnd > now) result[3] = curEnd;
        else if (nextStart != -1) result[3] = nextStart + durationMs;
        return result;
    }

    /** Window length: end-start on a 24h clock; equal times mean a full 24h window. */
    public static long durationMs(Schedule s) {
        int startM = s.startTime.getHour() * 60 + s.startTime.getMinute();
        int endM = s.endTime.getHour() * 60 + s.endTime.getMinute();
        int mins = ((endM - startM) % (24 * 60) + 24 * 60) % (24 * 60);
        if (mins == 0) mins = 24 * 60;
        return mins * 60_000L;
    }


    private static LocalDate[] candidatesForBlock(Schedule s, int block) {
        switch (s.freq) {
            case DAILY:
                return new LocalDate[]{ s.anchor.plusDays((long) block * s.interval) };

            case WEEKLY: {
                LocalDate weekMon = s.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .plusWeeks((long) block * s.interval);
                LocalDate[] out = new LocalDate[s.days.size()];
                int i = 0;
                for (DayOfWeek dow : s.days) { // TreeSet iterates Mon..Sun
                    out[i++] = weekMon.plusDays(dow.getValue() - 1);
                }
                return out;
            }

            case MONTHLY: {
                YearMonth ym = YearMonth.from(s.anchor).plusMonths((long) block * s.interval);
                int day = s.anchor.getDayOfMonth();
                return new LocalDate[]{ day <= ym.lengthOfMonth() ? ym.atDay(day) : null };
            }

            case YEARLY: {
                int year = s.anchor.getYear() + block * s.interval;
                MonthDay md = MonthDay.from(s.anchor);
                boolean feb29 = md.getMonthValue() == 2 && md.getDayOfMonth() == 29;
                if (feb29 && !Year.isLeap(year)) return new LocalDate[]{ null };
                return new LocalDate[]{ LocalDate.of(year, md.getMonthValue(), md.getDayOfMonth()) };
            }
        }
        return null;
    }

    /** Representative date of a block, for the give-up horizon check. */
    private static LocalDate blockAnchorDate(Schedule s, int block) {
        switch (s.freq) {
            case DAILY:   return s.anchor.plusDays((long) block * s.interval);
            case WEEKLY:  return s.anchor.plusWeeks((long) block * s.interval);
            case MONTHLY: return s.anchor.plusMonths((long) block * s.interval);
            case YEARLY:  return s.anchor.plusYears((long) block * s.interval);
        }
        return null;
    }

    private static long toMillis(LocalDate date, LocalTime time, ZoneId zone) {
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli();
    }
}
