package cn.itcast.dtx.tccdemo.bank2.service.impl;

import cn.itcast.dtx.tccdemo.bank2.dao.AccountInfoDao;
import cn.itcast.dtx.tccdemo.bank2.service.AccountInfoService;
import lombok.extern.slf4j.Slf4j;
import org.dromara.hmily.annotation.Hmily;
import org.dromara.hmily.core.concurrent.threadlocal.HmilyTransactionContextLocal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Administrator
 * @version 1.0
 **/
@Service
@Slf4j
public class AccountInfoServiceImpl implements AccountInfoService {

    @Autowired
    AccountInfoDao accountInfoDao;

    @Override
    @Hmily(confirmMethod="confirmMethod", cancelMethod="cancelMethod")
    public void updateAccountBalance(String accountNo, Double amount) {
        //获取全局事务id
        String transId = HmilyTransactionContextLocal.getInstance().get().getTransId();
        log.info("bank2 try 开始执行");

        //1. 幂等
        if (accountInfoDao.isExistTry(transId)>0){
            log.info("bank2  已经执行try ，无需重复执行");
            return ;
        }

        //2.  try悬挂判断（判断cancel、confirm有一个已经执行了）
        if(accountInfoDao.isExistConfirm(transId)>0 || accountInfoDao.isExistCancel(transId)>0){
            log.info("bank2 悬挂处理try ，cancel或confirm已经执行");
            return ;
        }

        //3. 加冻结金额
        if(accountInfoDao.addAccountFrozen(accountNo, amount)<=0){
            throw new RuntimeException("bank1 try 冻结金额失败");
        }

        //4. 插入try执行记录（用于幂等判断）
        accountInfoDao.addTry(transId);
        log.info("bank2 处理try完成");
    }

    /**
     * confirm方法
     * 	confirm幂等校验
     * 	正式增加金额
     * @param accountNo
     * @param amount
     */
    @Transactional
    public void confirmMethod(String accountNo, Double amount){
        //获取全局事务id
        String transId = HmilyTransactionContextLocal.getInstance().get().getTransId();
        log.info("bank2 开始执行 confirm");

        //1. 幂等性检验
        if (accountInfoDao.isExistConfirm(transId)>0){
            log.info("bank1 已经执行commit，无需重复执行");
            return ;
        }

        //2. commit空回滚处理 (必须try先执行才能执行commit)
        if (accountInfoDao.isExistTry(transId)==0){
            log.info("bank1 空回滚处理，try没有执行，不允许commit执行");
            return ;
        }

        //3. 执行扣减冻结金额
        if (accountInfoDao.confirmAccountFrozen(accountNo,amount)<=0){
            throw new RuntimeException("bank1 commit 执行扣减冻结金额失败");
        }

        //4. 添加confirm记录
        accountInfoDao.addConfirm(transId);
        log.info("bank2  confirm 结束执行");
    }



    /**
     * @param accountNo
     * @param amount
     */
    public void cancelMethod(String accountNo, Double amount){
        //获取全局事务id
        String transId = HmilyTransactionContextLocal.getInstance().get().getTransId();
        log.info("bank2  cancel 开始执行");

        // 1. cancel幂等校验
        if(accountInfoDao.isExistCancel(transId)>0){
            log.info("bank1 已经执行cancel，无需重复执行");
            return ;
        }

        //2. cancel空回滚处理，如果try没有执行，cancel不允许执行
        if(accountInfoDao.isExistTry(transId)==0){
            log.info("bank1 空回滚处理，try没有执行，不允许cancel执行");
            return ;
        }

        //3. 执行cancel (增加可用余额)
        accountInfoDao.cancelAccountFrozen(accountNo,amount);

        //4. 插入一条cancel的执行记录
        accountInfoDao.addCancel(transId);
        log.info("bank1  cancel 结束执行");
    }

}
