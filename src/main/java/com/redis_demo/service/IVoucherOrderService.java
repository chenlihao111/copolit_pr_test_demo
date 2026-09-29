package com.redis_demo.service;

import com.redis_demo.dto.Result;
import com.redis_demo.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {


    Result seckillVoucher(Long voucherId);

    void CreateVoucherOrder(VoucherOrder voucherOrder);
}
