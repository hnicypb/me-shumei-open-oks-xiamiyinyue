package me.shumei.open.oks.xiamiyinyue;

import java.io.IOException;
import java.util.HashMap;

import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;

import android.content.Context;

/**
 * 使签到类继承CommonData，以方便使用一些公共配置信息
 * @author wolforce
 *
 */
public class Signin extends CommonData {
	String resultFlag = "false";
	String resultStr = "未知错误！";
	
	/**
	 * <p><b>程序的签到入口</b></p>
	 * <p>在签到时，此函数会被《一键签到》调用，调用结束后本函数须返回长度为2的一维String数组。程序根据此数组来判断签到是否成功</p>
	 * @param ctx 主程序执行签到的Service的Context，可以用此Context来发送广播
	 * @param isAutoSign 当前程序是否处于定时自动签到状态<br />true代表处于定时自动签到，false代表手动打开软件签到<br />一般在定时自动签到状态时，遇到验证码需要自动跳过
	 * @param cfg “配置”栏内输入的数据
	 * @param user 用户名
	 * @param pwd 解密后的明文密码
	 * @return 长度为2的一维String数组<br />String[0]的取值范围限定为两个："true"和"false"，前者表示签到成功，后者表示签到失败<br />String[1]表示返回的成功或出错信息
	 */
	public String[] start(Context ctx, boolean isAutoSign, String cfg, String user, String pwd) {
		//把主程序的Context传送给验证码操作类，此语句在显示验证码前必须至少调用一次
		CaptchaUtil.context = ctx;
		//标识当前的程序是否处于自动签到状态，只有执行此操作才能在定时自动签到时跳过验证码
		CaptchaUtil.isAutoSign = isAutoSign;
		
		try{
			//登录地址
			String loginUrl = "http://www.xiami.com/web/login";
			//签到页面的地址
			String signPageUrl = "http://www.xiami.com/web";
			//签到地址
			String signUrl = "";
			//验证码地址
			String captchaUrl = "";
			
			//存放Cookies的HashMap
			HashMap<String, String> cookies = new HashMap<String, String>();
			//Jsoup的Response
			Response res;
			
			//设置登录需要提交的数据
			HashMap<String, String> postDatas = new HashMap<String, String>();
			postDatas.put("email", user);
			postDatas.put("password", pwd);
			postDatas.put("LoginButton", "登录");
			
			//使用Android的UA模拟手机访问虾米网的登录页面，检测当前IP是否需要填写验证码
			res = Jsoup.connect(loginUrl).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
			//保存访问页面后的Cookies，这一步非常重要
			cookies.putAll(res.cookies());
			
			//分析访问登录页面后返回的数据，判断是否需要输入验证码
			if(res.body().contains("验证码")) {
				//构造验证码地址
				captchaUrl = "http://www.xiami.com" + res.parse().select("form p>img").attr("src");
				//使用验证码操作类中的showCaptcha方法获取、显示验证码
				if(CaptchaUtil.showCaptcha(captchaUrl , UA_ANDROID, cookies, "虾米网", user, "登录需要验证码")) {
					if (CaptchaUtil.captcha_input.length() > 0) {
						//获取验证码成功，可以用CaptchaUtil.captcha_input继续做其他事了
						//把验证码追加到要POST的数据中
						postDatas.put("validate", CaptchaUtil.captcha_input);
					} else {
						//用户取消输入验证码
						this.resultFlag = "false";
						this.resultStr = "用户放弃输入验证码，登录失败";
						return new String[]{this.resultFlag,this.resultStr};
					}
				} else {
					//拉取验证码失败，签到失败
					this.resultFlag = "false";
					this.resultStr = "拉取验证码失败，无法登录";
					return new String[]{this.resultFlag,this.resultStr};
				}
			}
			
			//提交登录信息
			res = Jsoup.connect(loginUrl).data(postDatas).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
			cookies.putAll(res.cookies());
			
			//访问签到页面
			res = Jsoup.connect(signPageUrl).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
			cookies.putAll(res.cookies());
			
			//判断今天是否已经签过到
			if(res.parse().toString().contains("已连续签到"))
			{
				String continueDays = "";
				try {
					continueDays = "，" + res.parse().select("div.icon .idh").text();
				} catch (Exception e) {
					e.printStackTrace();
				}
				resultFlag = "true";
				resultStr = "今天已签过到" + continueDays;
			}
			else
			{
				try {
					//构造提交签到信息的URL
					signUrl = "http://www.xiami.com" + res.parse().getElementsByClass("check_in").first().attr("href");
					//提交签到请求，注意提交的时候要使用referrer函数设置“引用页”，否则请求有可能会被服务器拒绝
					res = Jsoup.connect(signUrl).cookies(cookies).userAgent(UA_ANDROID).referrer("http://www.xiami.com/web").timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
					
					//分析提交签到请求后返回的数据，判断今天是否已经签过到
					if(res.body().contains("已连续签到"))
					{
						resultFlag = "true";
						try {
							resultStr = "签到成功，" + res.parse().getElementsByClass("idh").eq(1).text();
						} catch (Exception e) {
							resultStr = "签到成功";
						}
					}
					else
					{
						resultFlag = "false";
						resultStr = "签到失败";
					}
				} catch (Exception e) {
					resultFlag = "false";
					resultStr = "提交签到请求时发生错误，签到失败";
				}
			}
			
			
		} catch (IOException e) {
			this.resultFlag = "false";
			this.resultStr = "连接超时";
			e.printStackTrace();
		} catch (Exception e) {
			this.resultFlag = "false";
			this.resultStr = "未知错误！";
			e.printStackTrace();
		}
		
		//返回签到结果给《一键签到》主程序
		return new String[]{resultFlag, resultStr};
	}
	
}
