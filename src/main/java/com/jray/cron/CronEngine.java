package com.jray.cron;

import com.jray.cron.field.CronFieldHandler;
import org.springframework.util.StringUtils;

import java.util.BitSet;

/**
 * Created by Jerry on 2014/7/22.
 */
public class CronEngine {

    private CronFieldHandler[] fieldHandlers;



    public long getNextFireTime(CronExpression expression){
        return this.getNextFireTime(expression, System.currentTimeMillis());
    }

    public long getNextFireTime(CronExpression expression, long millis) {

        // check argument
        if (expression == null){
            throw new IllegalArgumentException("CronExpression must not be null");
        }
        // parse express (and save field meta) if necessary
        if (!expression.isParsed()){
            expression.setFieldMeta(this.parseExpression(expression));
        }
        return -1;
    }

    private Object[] parseExpression(CronExpression expression){

        // check argument
        if (expression == null || expression.getExpression() == null) {
            throw new IllegalArgumentException("cron expression must not be null");
        }
        // check state
        if (this.fieldHandlers == null) {
            throw new IllegalStateException("must call setFieldHandlers() before parsing expression");
        }
        // split fields
        String[] fields = expression.getExpression().split("\\s+");
        // check field count (v.s. field handler)
        int count = fields.length;
        if (count > this.fieldHandlers.length) {
            throw new IllegalArgumentException(
                    String.format("expression contains more fields(%d) than handler(%d): %s",
                            count, this.fieldHandlers.length, expression.getExpression()));
        }
        // parse fields
        Object[] fieldMeta = new Object[count];
        for (int i=0; i<count; i++){
            fieldMeta[i] = this.fieldHandlers[i].setField(fields[i]);
        }
        return fieldMeta;
    }

    public void setFieldHandlers(CronFieldHandler[] fieldHandlers){

        this.fieldHandlers = fieldHandlers;
    }


    /**
     * 將 cron expression 切割為獨立欄位
     * @param expression
     * @return
     */
    private String[] splitExpression(String expression){

        if (expression == null){
            return new String[0];
        }
        String[] fields = expression.split("\\s+");
        if (fields == null || fields.length < 6) {
            throw new IllegalArgumentException("invalid cron expression: " + expression);
        }
        return fields;
        /*
        StringTokenizer st = new StringTokenizer(str, delimiters);
        List<String> tokens = new ArrayList<String>();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            token = token.trim();
            if (token.length() > 0) {
                tokens.add(token);
            }
        }
        return tokens.toArray(new String[tokens.size()]);
        */
    }

    /**
     * Parse the given pattern expression.
     */
    public void parse(String expression) throws IllegalArgumentException {

        String[] fields = this.splitExpression(expression);
        if (fields == null || fields.length < 6) {
            throw new IllegalArgumentException(String.format("invalid cron expression (\"%s\")",  expression));
        }
/*
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
        */
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
                            field + "' in expression \"" + "expression" + "\"");
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
                        field + "' in expression \"" + "this.expression" + "\"");
            }
            result[0] = Integer.valueOf(split[0]);
            result[1] = Integer.valueOf(split[1]);
        }
        if (result[0] >= max || result[1] >= max) {
            throw new IllegalArgumentException("Range exceeds maximum (" + max + "): '" +
                    field + "' in expression \"" + "this.expression" + "\"");
        }
        if (result[0] < min || result[1] < min) {
            throw new IllegalArgumentException("Range less than minimum (" + min + "): '" +
                    field + "' in expression \"" + "this.expression" + "\"");
        }
        return result;
    }



}
