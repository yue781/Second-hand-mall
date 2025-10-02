package cn.bugstack.service;

import java.io.IOException;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 微信服务
 * @create 2024-09-28 13:44
 */
public interface ILoginService {
    /*生成登录二维码的ticket*/
    String createQrCodeTicket() throws Exception;
    /*检查用户是否已登录*/
    String checkLogin(String ticket);
    /*保存登录状态并发送模板消息*/
    void saveLoginState(String ticket, String openid) throws IOException;

}
