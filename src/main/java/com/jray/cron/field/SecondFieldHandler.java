package com.jray.cron.field;

import java.util.BitSet;
import java.util.Calendar;

/**
 * Created by Jerry on 2014/7/22.
 */
public class SecondFieldHandler implements FieldHandler {

    private final int min = 0;
    private final int max = 60;

    @Override
    public Object parseField(String fieldExpression) {

        // valid symbol: , / - *

        BitSet result = new BitSet(this.max);

        // split by comma ','
        String[] fields = fieldExpression.split(",");
        for (String field : fields) {
            // contains incrementer
            if (field.contains("/")) {
                String[] split = field.split("/");
                if (split.length > 2) {
                    throw new IllegalArgumentException("Incrementer has more than two fields: '" + field + "'");
                }
                int[] range = this.getRange(split[0], min, max);
                if (!split[0].contains("-")) {
                    range[1] = max - 1;
                }
                int delta = Integer.valueOf(split[1]);
                for (int i = range[0]; i <= range[1]; i += delta) {
                    result.set(i);
                }
            }
            // enum, range or any
            else {
                int[] range = this.getRange(field, min, max);
                result.set(range[0], range[1] + 1);
            }
        }

        return result;
    }

    /**
     * 回傳最小最大值
     *
     * @param field
     * @param min
     * @param max
     * @return
     */
    private int[] getRange(String field, int min, int max) {
        int[] result = new int[2];
        // case: any
        if (field.contains("*")) {
            result[0] = min;
            result[1] = max - 1;
        }
        // case: range
        else if (field.contains("-")) {
            String[] split = field.split("-");
            if (split.length > 2) {
                throw new IllegalArgumentException("Range has more than two fields: '" + field + "'");
            }
            result[0] = Integer.valueOf(split[0]);
            result[1] = Integer.valueOf(split[1]);
        }
        // case: single value
        else {
            result[0] = result[1] = Integer.valueOf(field);
        }

        // check min and max
        if (result[0] >= max || result[1] >= max) {
            throw new IllegalArgumentException("Range exceeds maximum (" + max + "): '" + field + "'");
        }
        if (result[0] < min || result[1] < min) {
            throw new IllegalArgumentException("Range less than minimum (" + min + "): '" + field + "'");
        }
        return result;
    }

    private final int CURR_FIELD = Calendar.SECOND;
    private final int NEXT_FIELD = Calendar.MINUTE;


    @Override
    public boolean next(Calendar calendar, Object fieldMeta) {

        BitSet bits = (BitSet)fieldMeta;
        int original = calendar.get(Calendar.SECOND);
        int nextValue = bits.nextSetBit(original);

        // can't find next valid value: advance next-field and search from 0
        if (nextValue == -1) {
            calendar.add(NEXT_FIELD, 1);
            nextValue = bits.nextSetBit(0);
        }

        // 找到的值與目前不同 => 設定新值 + 將較小的欄位 reset
        if (nextValue != original) {
            calendar.set(CURR_FIELD, nextValue);
            return true;
        }
        else {
            return false;
        }
    }
}
