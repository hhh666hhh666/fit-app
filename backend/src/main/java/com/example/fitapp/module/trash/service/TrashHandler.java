package com.example.fitapp.module.trash.service;

import com.example.fitapp.module.nutrition.service.DietService;
import com.example.fitapp.module.trash.entity.TrashItem;

/** 一种数据怎么从回收站回去（ADR 0047）。每种类型一个实现，放在数据所属的模块里。 */
public interface TrashHandler {

    /** 对应 {@link TrashItem} 上的类型常量。 */
    String type();

    /** 按原 id 写回原表；恢复一定成功（ADR 0047）。有话要告诉主人（比如改了名）就返回，否则返回 null。 */
    String restore(Long userId, TrashItem item);

    /** 到期或彻底删除时，清掉快照之外还留着的东西（照片文件）。 */
    default void purge(TrashItem item) {
    }

    /** 列表里的缩略图；没有就返回 null。 */
    default DietService.PhotoData thumb(TrashItem item) {
        return null;
    }
}
