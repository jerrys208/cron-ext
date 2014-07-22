package com.jray.cron;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import org.springframework.util.StringUtils;

/**
 * @author jerry
 *
 *         org.springframework.scheduling.support.CronSequenceGenerator
 *
 *         1. 兩階段: <br>
 *         1.1 解析 expression <br>
 *         1.2 計算 expression <br>
 *
 */
public class CronUtil {

    private final BitSet seconds = new BitSet(60);

    private final BitSet minutes = new BitSet(60);

    private final BitSet hours = new BitSet(24);

    private final BitSet daysOfWeek = new BitSet(7);

    private final BitSet daysOfMonth = new BitSet(31);

    private final BitSet months = new BitSet(12);

    private final String expression;

    private final TimeZone timeZone;

    public CronUtil(String expression) {
        this(expression, TimeZone.getDefault());
    }

    public CronUtil(String expression, TimeZone timeZone) {
        this.expression = expression;
        this.timeZone = timeZone;
        parse(expression);
    }

    /**
     * 取得 "指定時間" 後的 下一個符合 cron expression 時間
     *
     * @param millis
     *            指定時間
     * @return 下一個符合 cron expression 時間
     */
    public long next(long millis) {

        // 設定指定的時間, 並捨棄 millisecond
        Calendar calendar = new GregorianCalendar();
        calendar.setTimeZone(this.timeZone);
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.MILLISECOND, 0);
        long originalTimestamp = calendar.getTimeInMillis();

        // 首次搜尋 (忽略毫秒)
        this.doNext(calendar, calendar.get(Calendar.YEAR));

        // 如果時間相等 => 需要 +1 秒再算一次, 確保取得的時間比 date 晚
        if (calendar.getTimeInMillis() == originalTimestamp) {
            calendar.add(Calendar.SECOND, 1);
            this.doNext(calendar, calendar.get(Calendar.YEAR));
        }

        // 回傳結果
        return calendar.getTimeInMillis();
    }

    /**
     * 搜尋下一個時間點
     *
     * @param calendar
     * @param baseYear
     */
    private void doNext(Calendar calendar, int baseYear) {
        long id = calendar.getTimeInMillis();
        System.err.println("doNext()[+]:" + id);

        // 記錄需要 reset 的欄位
        List<Integer> resets = new ArrayList<Integer>();

        // 秒
        int second = calendar.get(Calendar.SECOND);
        int updateSecond = findNext(this.seconds, second, calendar, Calendar.SECOND, Calendar.MINUTE, resets);
        // 沒有異動 => 加入 reset 清單 (沒異動代表下一個欄位進位時需要 reset)
        if (second == updateSecond) {
            resets.add(Calendar.SECOND);
        }

        // 分
        int minute = calendar.get(Calendar.MINUTE);
        int updateMinute = findNext(this.minutes, minute, calendar, Calendar.MINUTE, Calendar.HOUR_OF_DAY, resets);
        // 沒有異動 => 加入 reset 清單
        if (minute == updateMinute) {
            resets.add(Calendar.MINUTE);
        }
        // 已經異動 => 再次調整較小的欄位 (second)
        else {
            doNext(calendar, baseYear);
            return;
        }

        // 時
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int updateHour = findNext(this.hours, hour, calendar, Calendar.HOUR_OF_DAY, Calendar.DAY_OF_WEEK, resets);
        if (hour == updateHour) {
            resets.add(Calendar.HOUR_OF_DAY);
        }
        else {
            doNext(calendar, baseYear);
            return;
        }

        // 日 / 星期
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        int updateDayOfMonth = findNextDay(calendar, this.daysOfMonth, dayOfMonth, daysOfWeek, dayOfWeek, resets);
        if (dayOfMonth == updateDayOfMonth) {
            resets.add(Calendar.DAY_OF_MONTH);
        }
        else {
            doNext(calendar, baseYear);
            return;
        }

        // 月
        int month = calendar.get(Calendar.MONTH);
        int updateMonth = findNext(this.months, month, calendar, Calendar.MONTH, Calendar.YEAR, resets);
        if (month != updateMonth) {
            // 確認在合理範圍內
            if (calendar.get(Calendar.YEAR) - baseYear > 4) {
                throw new IllegalArgumentException("Invalid cron expression \"" + this.expression +
                        "\" led to runaway search for next trigger");
            }
            doNext(calendar, baseYear);
            return;
        }
        System.err.println("doNext()[-]:" + id);
    }

    private int findNextDay(Calendar calendar, BitSet daysOfMonth, int dayOfMonth, BitSet daysOfWeek, int dayOfWeek,
                            List<Integer> resets) {

        // 日期 / 星期 兩個條件需要一起滿足
        // 每次往前進一天並檢驗是否符合設定, 如有改變日期 => 將較小的欄位 reset.
        // 最多往前 366 天

        int count = 0;
        int max = 366;
        // the DAY_OF_WEEK values in java.util.Calendar start with 1 (Sunday),
        // but in the cron pattern, they start with 0, so we subtract 1 here
        while ((!daysOfMonth.get(dayOfMonth) || !daysOfWeek.get(dayOfWeek - 1)) && count++ < max) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
            reset(calendar, resets);
        }
        if (count >= max) {
            throw new IllegalArgumentException("Overflow in day for expression \"" + this.expression + "\"");
        }
        return dayOfMonth;
    }

    /**
     * Search the bits provided for the next set bit after the value provided, and reset the calendar.
     *
     * @param bits
     *            a {@link BitSet} representing the allowed values of the field
     * @param value
     *            the current value of the field
     * @param calendar
     *            the calendar to increment as we move through the bits
     * @param field
     *            the field to increment in the calendar (@see {@link Calendar} for the static constants defining valid
     *            fields)
     * @param lowerOrders
     *            the Calendar field ids that should be reset (i.e. the ones of lower significance than the field of
     *            interest)
     * @return the value of the calendar field that is next in the sequence
     */
    private int findNext(BitSet bits, int value, Calendar calendar, int field, int nextField, List<Integer> lowerOrders) {

        // 在 BitSet 中尋找下一個合法值
        int nextValue = bits.nextSetBit(value);

        // 找不到 => 下一個 Field 需要進位, 然後由 0 再找一次
        if (nextValue == -1) {
            calendar.add(nextField, 1);
            this.reset(calendar, Arrays.asList(field)); // TODO 應該不需要 ? 底下會設定.
            nextValue = bits.nextSetBit(0);
        }

        // 找到的值與目前不同 => 設定新值 + 將較小的欄位 reset
        if (nextValue != value) {
            calendar.set(field, nextValue);
            this.reset(calendar, lowerOrders);
        }
        return nextValue;
    }

    /**
     * Reset the calendar setting all the fields provided to zero.
     */
    private void reset(Calendar calendar, List<Integer> fields) {
        for (int field : fields) {
            calendar.set(field, field == Calendar.DAY_OF_MONTH ? 1 : 0);
        }
    }

    // Parsing logic invoked by the constructor

    /**
     * Parse the given pattern expression.
     */
    private void parse(String expression) throws IllegalArgumentException {
        String[] fields = StringUtils.tokenizeToStringArray(expression, " ");
        if (fields.length != 6) {
            throw new IllegalArgumentException(String.format(
                    "Cron expression must consist of 6 fields (found %d in \"%s\")", fields.length, expression));
        }
        setNumberHits(this.seconds, fields[0], 0, 60);
        setNumberHits(this.minutes, fields[1], 0, 60);
        setNumberHits(this.hours, fields[2], 0, 24);
        setDaysOfMonth(this.daysOfMonth, fields[3]);
        setMonths(this.months, fields[4]);
        setDays(this.daysOfWeek, replaceOrdinals(fields[5], "SUN,MON,TUE,WED,THU,FRI,SAT"), 8);
        if (this.daysOfWeek.get(7)) {
            // Sunday can be represented as 0 or 7
            this.daysOfWeek.set(0);
            this.daysOfWeek.clear(7);
        }
    }

    /**
     * Replace the values in the commaSeparatedList (case insensitive) with their index in the list.
     *
     * @return a new string with the values from the list replaced
     */
    private String replaceOrdinals(String value, String commaSeparatedList) {
        String[] list = StringUtils.commaDelimitedListToStringArray(commaSeparatedList);
        for (int i = 0; i < list.length; i++) {
            String item = list[i].toUpperCase();
            value = StringUtils.replace(value.toUpperCase(), item, "" + i);
        }
        return value;
    }

    private void setDaysOfMonth(BitSet bits, String field) {
        int max = 31;
        // Days of month start with 1 (in Cron and Calendar) so add one
        setDays(bits, field, max + 1);
        // ... and remove it from the front
        bits.clear(0);
    }

    private void setDays(BitSet bits, String field, int max) {
        if (field.contains("?")) {
            field = "*";
        }
        setNumberHits(bits, field, 0, max);
    }

    private void setMonths(BitSet bits, String value) {
        int max = 12;
        value = replaceOrdinals(value, "FOO,JAN,FEB,MAR,APR,MAY,JUN,JUL,AUG,SEP,OCT,NOV,DEC");
        BitSet months = new BitSet(13);
        // Months start with 1 in Cron and 0 in Calendar, so push the values first into a longer bit set
        setNumberHits(months, value, 1, max + 1);
        // ... and then rotate it to the front of the months
        for (int i = 1; i <= max; i++) {
            if (months.get(i)) {
                bits.set(i - 1);
            }
        }
    }

    private void setNumberHits(BitSet bits, String value, int min, int max) {
        String[] fields = StringUtils.delimitedListToStringArray(value, ",");
        for (String field : fields) {
            if (!field.contains("/")) {
                // Not an incrementer so it must be a range (possibly empty)
                int[] range = getRange(field, min, max);
                bits.set(range[0], range[1] + 1);
            }
            else {
                String[] split = StringUtils.delimitedListToStringArray(field, "/");
                if (split.length > 2) {
                    throw new IllegalArgumentException("Incrementer has more than two fields: '" +
                            field + "' in expression \"" + this.expression + "\"");
                }
                int[] range = getRange(split[0], min, max);
                if (!split[0].contains("-")) {
                    range[1] = max - 1;
                }
                int delta = Integer.valueOf(split[1]);
                for (int i = range[0]; i <= range[1]; i += delta) {
                    bits.set(i);
                }
            }
        }
    }

    private int[] getRange(String field, int min, int max) {
        int[] result = new int[2];
        if (field.contains("*")) {
            result[0] = min;
            result[1] = max - 1;
            return result;
        }
        if (!field.contains("-")) {
            result[0] = result[1] = Integer.valueOf(field);
        }
        else {
            String[] split = StringUtils.delimitedListToStringArray(field, "-");
            if (split.length > 2) {
                throw new IllegalArgumentException("Range has more than two fields: '" +
                        field + "' in expression \"" + this.expression + "\"");
            }
            result[0] = Integer.valueOf(split[0]);
            result[1] = Integer.valueOf(split[1]);
        }
        if (result[0] >= max || result[1] >= max) {
            throw new IllegalArgumentException("Range exceeds maximum (" + max + "): '" +
                    field + "' in expression \"" + this.expression + "\"");
        }
        if (result[0] < min || result[1] < min) {
            throw new IllegalArgumentException("Range less than minimum (" + min + "): '" +
                    field + "' in expression \"" + this.expression + "\"");
        }
        return result;
    }

    String getExpression() {
        return this.expression;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CronUtil)) {
            return false;
        }
        CronUtil cron = (CronUtil) obj;
        return cron.months.equals(this.months) && cron.daysOfMonth.equals(this.daysOfMonth)
                && cron.daysOfWeek.equals(this.daysOfWeek) && cron.hours.equals(this.hours)
                && cron.minutes.equals(this.minutes) && cron.seconds.equals(this.seconds);
    }

    @Override
    public int hashCode() {
        return 37 + 17 * this.months.hashCode() + 29 * this.daysOfMonth.hashCode() + 37 * this.daysOfWeek.hashCode()
                + 41 * this.hours.hashCode() + 53 * this.minutes.hashCode() + 61 * this.seconds.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + this.expression;
    }

    /**
     * @param args
     */
    public static void main(String[] args) {

        System.out.println(new Date(Long.MAX_VALUE));

        long now = System.currentTimeMillis();
        CronUtil cu = new CronUtil("* 4 3 * * ?");
        long next = cu.next(now);
        System.out.println("1:" + now);
        System.out.println("2:" + next + "," + new Date(next));
    }
}
