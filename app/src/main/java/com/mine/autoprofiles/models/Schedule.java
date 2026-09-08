package com.mine.autoprofiles.models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TreeSet;

/**
 * Schedule definition for a TIME trigger, stored as JSON in triggers.value.
 * Modeled after the iCalendar RRULE concepts used by calendar apps (Etar etc.):
 * one-time events with explicit start/end datetimes, or recurring events with
 * DAILY / WEEKLY / MONTHLY / YEARLY frequency, an "every X" interval, and a
 * termination of never / until-date / occurrence-count.
 *
 * The legacy pre-JSON format "HH:MM-HH:MM|d,d,..." (d = java.util.Calendar
 * day-of-week, 1=Sun..7=Sat) is still parsed and treated as WEEKLY / every 1
 * week / no expiration, so rules created by older versions keep working.
 */
public class Schedule {

    public enum Mode { ONCE, RECUR }
    public enum Freq { DAILY, WEEKLY, MONTHLY, YEARLY }
    public enum Term { NEVER, UNTIL, COUNT }

    // --- ONCE ---
    public LocalDateTime onceStart;
    public LocalDateTime onceEnd;

    // --- RECUR ---
    public LocalTime startTime;
    public LocalTime endTime;
    /** First day the rule may occur; also defines day-of-month / month-day for MONTHLY/YEARLY. */
    public LocalDate anchor;
    public Freq freq = Freq.DAILY;
    public int interval = 1;
    /** WEEKLY only. */
    public TreeSet<DayOfWeek> days = new TreeSet<>();

    public Mode mode = Mode.RECUR;
    public Term term = Term.NEVER;
    public LocalDate until;   // Term.UNTIL (inclusive)
    public int count;         // Term.COUNT

    private static final DateTimeFormatter D = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    // ------------------------------------------------------------------ parse

    /** @return parsed schedule, or null if the value is unusable. */
    public static Schedule parse(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.startsWith("{")) return parseJson(v);
        return parseLegacy(v);
    }

    private static Schedule parseJson(String v) {
        try {
            JSONObject o = new JSONObject(v);
            Schedule s = new Schedule();
            s.mode = Mode.valueOf(o.getString("mode"));
            if (s.mode == Mode.ONCE) {
                s.onceStart = LocalDateTime.parse(o.getString("start"), DT);
                s.onceEnd = LocalDateTime.parse(o.getString("end"), DT);
            } else {
                s.startTime = LocalTime.parse(o.getString("startTime"), T);
                s.endTime = LocalTime.parse(o.getString("endTime"), T);
                s.anchor = LocalDate.parse(o.getString("anchor"), D);
                s.freq = Freq.valueOf(o.getString("freq"));
                s.interval = Math.max(1, o.optInt("interval", 1));
                JSONArray arr = o.optJSONArray("days");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        s.days.add(DayOfWeek.of(arr.getInt(i))); // ISO 1=Mon..7=Sun
                    }
                }
                s.term = Term.valueOf(o.optString("term", "NEVER"));
                if (s.term == Term.UNTIL) s.until = LocalDate.parse(o.getString("until"), D);
                if (s.term == Term.COUNT) s.count = Math.max(1, o.getInt("count"));
                if (s.freq == Freq.WEEKLY && s.days.isEmpty()) return null;
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** "08:00-17:00|2,3,4" with java.util.Calendar day numbers (1=Sun..7=Sat). */
    private static Schedule parseLegacy(String v) {
        try {
            String[] parts = v.split("\\|");
            String[] times = parts[0].split("-");
            Schedule s = new Schedule();
            s.mode = Mode.RECUR;
            s.freq = Freq.WEEKLY;
            s.interval = 1;
            s.term = Term.NEVER;
            s.startTime = LocalTime.parse(times[0].trim(), T);
            s.endTime = times.length > 1 ? LocalTime.parse(times[1].trim(), T) : s.startTime;
            // Anchor safely in the past: legacy rules had no start date semantics.
            s.anchor = LocalDate.now().minusDays(7);
            for (String d : parts[1].split(",")) {
                int cal = Integer.parseInt(d.trim());          // 1=Sun..7=Sat
                s.days.add(DayOfWeek.of(cal == 1 ? 7 : cal - 1)); // -> ISO 1=Mon..7=Sun
            }
            if (s.days.isEmpty()) return null;
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------- serialize

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("mode", mode.name());
            if (mode == Mode.ONCE) {
                o.put("start", onceStart.format(DT));
                o.put("end", onceEnd.format(DT));
            } else {
                o.put("startTime", startTime.format(T));
                o.put("endTime", endTime.format(T));
                o.put("anchor", anchor.format(D));
                o.put("freq", freq.name());
                o.put("interval", interval);
                if (freq == Freq.WEEKLY) {
                    JSONArray arr = new JSONArray();
                    for (DayOfWeek d : days) arr.put(d.getValue());
                    o.put("days", arr);
                }
                o.put("term", term.name());
                if (term == Term.UNTIL) o.put("until", until.format(D));
                if (term == Term.COUNT) o.put("count", count);
            }
            return o.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Schedule.toJson failed", e);
        }
    }

    // -------------------------------------------------------------- describe

    private static final DateTimeFormatter HUMAN_DT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault());
    private static final DateTimeFormatter HUMAN_D =
            DateTimeFormatter.ofPattern("MMM d yyyy", Locale.getDefault());
    private static final DateTimeFormatter HUMAN_MD =
            DateTimeFormatter.ofPattern("MMM d", Locale.getDefault());

    /** Short human-readable summary for the rule list. */
    public String describe() {
        if (mode == Mode.ONCE) {
            if (onceStart.toLocalDate().equals(onceEnd.toLocalDate())) {
                return onceStart.format(HUMAN_DT) + "-" + onceEnd.format(T);
            }
            return onceStart.format(HUMAN_DT) + " \u2192 " + onceEnd.format(HUMAN_DT);
        }

        StringBuilder b = new StringBuilder();
        b.append(startTime.format(T)).append("-").append(endTime.format(T)).append(" ");
        switch (freq) {
            case DAILY:
                b.append(interval == 1 ? "daily" : "every " + interval + " days");
                break;
            case WEEKLY:
                b.append(interval == 1 ? "weekly on " : "every " + interval + " weeks on ");
                boolean first = true;
                for (DayOfWeek d : days) { // TreeSet: Mon..Sun (ISO order)
                    if (!first) b.append(", ");
                    b.append(d.name().charAt(0)).append(d.name().substring(1, 3).toLowerCase(Locale.US));
                    first = false;
                }
                break;
            case MONTHLY:
                b.append(interval == 1 ? "monthly" : "every " + interval + " months")
                        .append(" on day ").append(anchor.getDayOfMonth());
                break;
            case YEARLY:
                b.append(interval == 1 ? "yearly" : "every " + interval + " years")
                        .append(" on ").append(anchor.format(HUMAN_MD));
                break;
        }
        if (term == Term.UNTIL) b.append(" until ").append(until.format(HUMAN_D));
        if (term == Term.COUNT) b.append(" (").append(count).append("x)");
        return b.toString();
    }
}
