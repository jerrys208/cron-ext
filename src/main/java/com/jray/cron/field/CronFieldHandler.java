package com.jray.cron.field;

/**
 * Created by Jerry on 2014/7/22.
 */
public interface CronFieldHandler {


    public Object setField(String field);

    public void next(long baseTime);
}
