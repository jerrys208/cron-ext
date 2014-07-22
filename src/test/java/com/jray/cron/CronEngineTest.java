package com.jray.cron;

import org.junit.Test;

/**
 * Created by Jerry on 2014/7/22.
 */
public class CronEngineTest {

    @Test
    public void testSplitExpression(){

        String expr = "* 1 2  3";
        String[] res = expr.split("\\s+");
        for(String token: res){
            System.out.println("token:[" + token + "]");
        }

    }
}
