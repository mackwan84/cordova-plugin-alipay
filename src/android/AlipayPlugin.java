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

import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.widget.Toast;

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

    private static final int SDK_PAY_FLAG = 1;

    private static final String TAG = "Alipay";

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        partner = webView.getPreferences().getString("partner", "");
        seller = webView.getPreferences().getString("seller", "");
        rsa_private_key = webView.getPreferences().getString("rsa_private", "");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("pay".equals(action)) {
            try {
                JSONObject obj = args.getJSONObject(0);
                this.pay(obj, callbackContext);
            } catch (Exception e) {
                return false;
            }
            return true;
        }
        return false;
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message message) {
            switch (message.what) {
                case SDK_PAY_FLAG: {
                    PayResult payResult = new PayResult((String) message.obj);
                    /**
                     * 同步返回的结果必须放置到服务端进行验证（验证的规则请看https://doc.open.alipay.com/doc2/
                     * detail.htm?spm=0.0.0.0.xdvAU6&treeId=59&articleId=103665&
                     * docType=1) 建议商户依赖异步通知
                     */
                    String resultInfo = payResult.getResult();// 同步返回需要验证的信息

                    String resultStatus = payResult.getResultStatus();
                    // 判断resultStatus 为“9000”则代表支付成功，具体状态码代表含义可参考接口文档
                    if (TextUtils.equals(resultStatus, "9000")) {
                        Toast.makeText(cordova.getActivity(), "支付成功", Toast.LENGTH_SHORT).show();
                    } else {
                        // 判断resultStatus 为非"9000"则代表可能支付失败
                        // "8000"代表支付结果因为支付渠道原因或者系统原因还在等待支付结果确认，最终交易是否成功以服务端异步通知为准（小概率状态）
                        if (TextUtils.equals(resultStatus, "8000")) {
                            Toast.makeText(cordova.getActivity(), "支付结果确认中", Toast.LENGTH_SHORT).show();

                        } else {
                            // 其他值就可以判断为支付失败，包括用户主动取消支付，或者系统返回的错误
                            Toast.makeText(cordova.getActivity(), "支付失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
                }
                default:
                    break;
            }
        };
    };

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
				orderInfo += "&currency" + "\"" + obj.getString("currency") + "\"";
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

                Message message = new Message();
                message.what = SDK_PAY_FLAG;
                message.obj = result;
                mHandler.sendMessage(message);

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
