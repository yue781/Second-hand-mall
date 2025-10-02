package cn.bugstack.domain.res;

import lombok.Data;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 获取 Access token DTO 对象
 * @create 2024-09-28 13:32
 */

//这个类用于接收微信服务器返回的token信息
@Data
public class WeixinTokenRes {

    private String access_token;
    private int expires_in;
    private String errcode;
    private String errmsg;

}
