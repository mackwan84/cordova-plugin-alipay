package cn.com.icon.cordova;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.text.TextUtils;

import com.alipay.sdk.app.PayTask;

/**
 * This class echoes a string called from JavaScript.
 */
public class AlipayPlugin extends CordovaPlugin {
    // 商户PID
    private String partner = "";
    // 商户收款账号
    private String seller = "";
    // 商户私钥，pkcs8格式
    private String rsa_private_key = "";

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        partner = webView.getPreferences().getString("partner", "");
        seller = webView.getPreferences().getString("seller", "");
        rsa_private_key = webView.getPreferences().getString("rsa_private_key", "");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("pay".equals(action)) {
            try {
                JSONObject obj = args.getJSONObject(0);
                this.pay(obj, callbackContext);
            } catch (JSONException e) {
				callbackContext.error(new JSONObject());
				e.printStackTrace();
				return false;
			}
            return true;
        }
        return false;
    }

    /**
     * create the order info. 创建订单信息
     */
    private String getOrderInfo(JSONObject obj) {
        // 签约合作者身份ID
        String orderInfo = "partner=" + "\"" + partner + "\"";

        // 签约卖家支付宝账号
        orderInfo += "&seller_id=" + "\"" + seller + "\"";

		try {
			// 商户网站唯一订单号
			orderInfo += "&out_trade_no=" + "\"" + obj.getString("out_trade_no") + "\"";
		} catch (JSONException e) {
			e.printStackTrace();
		}

		try {
			// 商品名称
			orderInfo += "&subject=" + "\"" + obj.getString("subject") + "\"";
		} catch (JSONException e) {
			e.printStackTrace();
		}

		try {
			// 商品详情
			orderInfo += "&body=" + "\"" + obj.getString("body") + "\"";
		} catch (JSONException e) {
			e.printStackTrace();
		}

		try {
			// 商品金额
			orderInfo += "&total_fee=" + "\"" + obj.getDouble("total_fee") + "\"";
		} catch (JSONException e) {
			e.printStackTrace();
		}

		try {
			// 商品货币
			if(obj.getString("currency")!=null && !TextUtils.isEmpty(obj.getString("currency"))) {
				orderInfo += "&currency=" + "\"" + obj.getString("currency") + "\"";
				orderInfo += "&forex_biz=\"FP\"";
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}

		try {
			// 服务器异步通知页面路径
			orderInfo += "&notify_url=" + "\"" + obj.getString("notify_url") + "\"";
		} catch (JSONException e) {
			e.printStackTrace();
		}

		try {
			// 支付宝处理完请求后，当前页面跳转到商户指定页面的路径，可空
			if(obj.getString("return_url")!=null && !TextUtils.isEmpty(obj.getString("return_url"))) {
				orderInfo += "&return_url=" + "\"" + obj.getString("return_url") + "\"";
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}

        // 服务接口名称， 固定值
        orderInfo += "&service=\"mobile.securitypay.pay\"";

        // 支付类型， 固定值
        orderInfo += "&payment_type=\"1\"";

        // 参数编码， 固定值
        orderInfo += "&_input_charset=\"utf-8\"";

        return orderInfo;
    }

    /**
     * sign the order info. 对订单信息进行签名
     *
     * @param content 待签名订单信息
     */
    private String sign(String content) {
        return SignUtils.sign(content, rsa_private_key);
    }

    /**
     * get the sign type we use. 获取签名方式
     */
    private String getSignType() {
        return "sign_type=\"RSA\"";
    }

    private void pay(final JSONObject obj, final CallbackContext callbackContext) {
        Runnable payRunnable = new Runnable() {
            @Override
            public void run() {
                // 构造PayTask 对象
                PayTask alipay = new PayTask(cordova.getActivity());
				
				// 订单
				String orderInfo = getOrderInfo(obj);

				// 对订单做RSA 签名
				String sign = sign(orderInfo);
				try {
					// 仅需对sign 做URL编码
					sign = URLEncoder.encode(sign, "UTF-8");
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}

				// 完整的符合支付宝参数规范的订单信息
				String payInfo = orderInfo + "&sign=\"" + sign + "\"&" + getSignType();
				
                // 调用支付接口，获取支付结果
                String result = alipay.pay(payInfo, true);

                PayResult payResult = new PayResult(result);
                if (TextUtils.equals(payResult.getResultStatus(), "9000")) {
                    callbackContext.success(payResult.toJson());
                } else {
                    if (TextUtils.equals(payResult.getResultStatus(), "8000")) {
                        callbackContext.success(payResult.toJson());
                    } else {
                        callbackContext.error(payResult.toJson());
                    }
                }
            }
        };

        // 必须异步调用
        cordova.getThreadPool().execute(payRunnable);
    }
}
