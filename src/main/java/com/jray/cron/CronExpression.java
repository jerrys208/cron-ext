package com.jray.cron;

import com.jray.cron.field.FieldMeta;

import java.util.TimeZone;

/**
 * Created by Jerry on 2014/7/22.
 *
 * - 支援的 expression 為:
 *  1. 6 個欄位: [秒] [分] [時] [日] [月] [週]
 *  2. 7 個欄位: [秒] [分] [時] [日] [月] [週] [年]
 *
 */
public class CronExpression {

    private final String expression;

    private final TimeZone timeZone;

    private FieldMeta[] fieldMeta;

    public CronExpression(String expression) {
        this(expression, TimeZone.getDefault());
    }

    public CronExpression(String expression, TimeZone timeZone) {
        this.expression = expression;
        this.timeZone = timeZone;
    }

    public boolean isParsed(){
        return this.fieldMeta != null;
    }

    public void setFieldMeta(FieldMeta[] fieldMeta) {
        this.fieldMeta = fieldMeta;
    }

    public Object[] getFiledMeta() {
        return this.fieldMeta;
    }

    public String getExpression() {
        return expression;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }
}
