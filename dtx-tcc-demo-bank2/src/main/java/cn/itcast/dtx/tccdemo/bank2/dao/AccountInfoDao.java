package cn.itcast.dtx.tccdemo.bank2.dao;

import org.apache.ibatis.annotations.*;
import org.springframework.stereotype.Component;

@Component
@Mapper
public interface AccountInfoDao {

    /**
     * 添加冻结金额
     * @param accountNo
     * @param amount
     * @return
     */
    @Update("update account_info set account_frozen=account_frozen + #{amount} where  account_no=#{accountNo} ")
    int addAccountFrozen(@Param("accountNo") String accountNo, @Param("amount") Double amount);

    /**
     * 添加冻结金额
     * @param accountNo
     * @param amount
     * @return
     */
    @Update("update account_info set account_frozen=account_frozen - #{amount},account_balance = account_balance + #{amount} where  account_no=#{accountNo} ")
    int confirmAccountFrozen(@Param("accountNo") String accountNo, @Param("amount") Double amount);

    /**
     * 清除冻结金额
     * @param accountNo
     * @param amount
     * @return
     */
    @Update("update account_info set account_frozen=account_frozen - #{amount} where  account_no=#{accountNo} ")
    int cancelAccountFrozen(@Param("accountNo") String accountNo, @Param("amount") Double amount);

    /**
     * 增加某分支事务try执行记录
     * @param localTradeNo 本地事务编号
     * @return
     */
    @Insert("insert into local_try_log values(#{txNo},now());")
    int addTry(@Param("txNo") String localTradeNo);

    @Insert("insert into local_confirm_log values(#{txNo},now());")
    int addConfirm(@Param("txNo") String localTradeNo);

    @Insert("insert into local_cancel_log values(#{txNo},now());")
    int addCancel(@Param("txNo") String localTradeNo);

    /**
     * 查询分支事务try是否已执行
     * @param localTradeNo 本地事务编号
     * @return
     */
    @Select("select count(1) from local_try_log where tx_no = #{txNo} ")
    int isExistTry(@Param("txNo") String localTradeNo);
    /**
     * 查询分支事务confirm是否已执行
     * @param localTradeNo 本地事务编号
     * @return
     */
    @Select("select count(1) from local_confirm_log where tx_no = #{txNo} ")
    int isExistConfirm(@Param("txNo") String localTradeNo);

    /**
     * 查询分支事务cancel是否已执行
     * @param localTradeNo 本地事务编号
     * @return
     */
    @Select("select count(1) from local_cancel_log where tx_no = #{txNo} ")
    int isExistCancel(@Param("txNo") String localTradeNo);

}
