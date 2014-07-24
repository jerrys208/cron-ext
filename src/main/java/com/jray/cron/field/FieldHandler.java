package com.jray.cron.field;

import java.util.Calendar;

/**
 * Created by Jerry on 2014/7/22.
 */
public interface FieldHandler {

    public int getField();
    public int getNextField();  // for 往前推進
    public int getPrevField();  // for 重新設置


    /**
     * 選取適當的欄位並計算對定的 FieldMeta
     *
     * @param fields cron expression 中的各欄位
     * @return 對應的 field-meta 物件
     */
    public FieldMeta parseFields(String[] fields);


    /**
     * 試圖尋找下一個符合的值
     *
     * @param calendar 尋找的起點
     * @param fieldMeta 已解析的 field meta
     * @return 是否找到新的值
     */
    public boolean seekNext(Calendar calendar, FieldMeta fieldMeta);




    public Object parseField(String fieldExpression);

    public boolean next(Calendar calendar, Object fieldMeta);
}
