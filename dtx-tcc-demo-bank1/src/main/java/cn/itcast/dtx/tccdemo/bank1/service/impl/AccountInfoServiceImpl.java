package cn.itcast.dtx.tccdemo.bank1.service.impl;

import cn.itcast.dtx.tccdemo.bank1.dao.AccountInfoDao;
import cn.itcast.dtx.tccdemo.bank1.service.AccountInfoService;
import cn.itcast.dtx.tccdemo.bank1.spring.Bank2Client;
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

    @Autowired
    Bank2Client bank2Client;


    /**
     * try幂等校验
     * try悬挂处理
     * 检查余额是够扣减金额
     * 扣减金额
     *
     * @param accountNo
     * @param amount
     */
    @Override
    //只要标记@Hmily就是try方法，在注解中指定confirm、cancel两个方法的名字
    @Hmily(confirmMethod = "commit", cancelMethod = "rollback")
    public void updateAccountBalance(String accountNo, Double amount) {
        String transId = HmilyTransactionContextLocal.getInstance().get().getTransId();
        updateLocal(transId, accountNo, amount);

        //5. 远程调用李四 （转账）
        if (!bank2Client.transfer(amount)) {
            throw new RuntimeException("bank1 远程调用李四微服务失败");
        }
        if (amount == 2) {
            throw new RuntimeException("bank1  人为制造异常");
        }
        log.info("bank1  执行try成功");
    }

    @Transactional
    public void updateLocal(String transId, String accountNo, Double amount) {
        //获取全局事务id
        log.info("bank1  开始执行 try");
        //1. 幂等判断（判断try是否已经执行）
        if (accountInfoDao.isExistTry(transId) > 0) {
            log.info("bank1  已经执行try ，无需重复执行");
            return;
        }

        //2. try悬挂判断（判断cancel、confirm有一个已经执行了）
        if (accountInfoDao.isExistConfirm(transId) > 0 || accountInfoDao.isExistCancel(transId) > 0) {
            log.info("bank1 悬挂处理try ，cancel或confirm已经执行");
            return;
        }

        //3. 冻结金额
        if (accountInfoDao.tryAccountFrozen(accountNo, amount) <= 0) {
            throw new RuntimeException("bank1 try 冻结金额失败");
        }

        //4. 插入try执行记录（用于幂等判断）
        accountInfoDao.addTry(transId);
    }

    /**
     * confirm方法
     *
     * @param accountNo
     * @param amount
     */
    @Transactional
    public void commit(String accountNo, Double amount) {
        //获取全局事务id
        String transId = HmilyTransactionContextLocal.getInstance().get().getTransId();
        log.info("bank1  开始执行 confirm ");

        //1. 幂等性检验
        if (accountInfoDao.isExistConfirm(transId) > 0) {
            log.info("bank1 已经执行commit，无需重复执行");
            return;
        }

        //2. commit空回滚处理 (必须try先执行才能执行commit)
        if (accountInfoDao.isExistTry(transId) == 0) {
            log.info("bank1 空回滚处理，try没有执行，不允许commit执行");
            return;
        }

        //3. 执行扣减冻结金额
        if (accountInfoDao.confirmAccountFrozen(accountNo, amount) <= 0) {
            throw new RuntimeException("bank1 commit 执行扣减冻结金额失败");
        }

        //4. 添加confirm记录
        accountInfoDao.addConfirm(transId);
        log.info("bank1  commit 结束执行");
    }


    /**
     * cancel方法
     * cancel幂等校验
     * cancel空回滚处理
     * 增加可用余额
     *
     * @param accountNo
     * @param amount
     */
    @Transactional
    public void rollback(String accountNo, Double amount) {
        //获取全局事务id
        String transId = HmilyTransactionContextLocal.getInstance().get().getTransId();
        log.info("bank1 开始执行 cancel");

        // 1. cancel幂等校验
        if (accountInfoDao.isExistCancel(transId) > 0) {
            log.info("bank1 已经执行cancel，无需重复执行");
            return;
        }

        //2. cancel空回滚处理，如果try没有执行，cancel不允许执行
        if (accountInfoDao.isExistTry(transId) == 0) {
            log.info("bank1 空回滚处理，try没有执行，不允许cancel执行");
            return;
        }

        //3. 执行cancel (增加可用余额)
        accountInfoDao.cancelAccountFrozen(accountNo, amount);

        //4. 插入一条cancel的执行记录
        accountInfoDao.addCancel(transId);
        log.info("bank1  cancel 结束执行");
    }

}
