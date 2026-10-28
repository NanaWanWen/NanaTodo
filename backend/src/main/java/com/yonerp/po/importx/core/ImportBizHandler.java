package com.yonerp.po.importx.core;

import com.yonerp.po.importx.entity.SysImportError;

import java.util.List;
import java.util.Map;

/**
 * Excel 导入业务处理器接口
 *
 * <p>每种业务（订单/收货/发票/付款）实现一个处理器，负责：
 * <ul>
 *   <li>模板校验（表头是否匹配当前模板版本）</li>
 *   <li>行级校验（必填、基础资料匹配、业务规则）</li>
 *   <li>确认导入（调用现有业务 Service，在事务中写入正式表）</li>
 * </ul>
 */
public interface ImportBizHandler {

    /** 支持的业务类型 */
    ImportBizType bizType();

    /** 当前模板版本 */
    String templateVersion();

    /** 模板表头（按列顺序） */
    List<String> templateHeaders();

    /**
     * 行级校验。
     *
     * @param rowIndex Excel 行号（从 1 开始，不含表头）
     * @param row      原始行数据（列标题 → 单元格值）
     * @return 错误列表；返回空列表表示该行校验通过
     */
    List<SysImportError> validateRow(int rowIndex, Map<String, String> row);

    /**
     * 确认导入。只处理校验通过的行；实现在事务中调用现有业务 Service。
     *
     * @param rows 校验通过的行数据（列标题 → 单元格值）
     * @return 每行对应的业务 ID（与入参顺序一致）
     */
    List<Long> confirm(List<Map<String, String>> rows);
}
