package com.btpay;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.json.JSONObject;

import com.braintreegateway.BraintreeGateway;
import com.braintreegateway.ClientTokenRequest;
import com.braintreegateway.CustomerRequest;
import com.braintreegateway.Environment;
import com.braintreegateway.PaymentMethodNonce;
import com.braintreegateway.Result;
import com.braintreegateway.ThreeDSecureInfo;
import com.braintreegateway.Transaction;
import com.braintreegateway.TransactionRequest;

public class BrainTreePaymentUtils {


	private static BraintreeGateway gateway = null;
	private static String merchantIdForToken = null;
	private static String privateKey = null;
	private static String publicKey = null;
	private static String merchantId = null;
	private static String environmentType = null;
	private static String merchantIdIndia = null;
	private static String merchantIdDefualt = null;

	/**
	 * Get Braintree token.
	 * 
	 * @param String
	 *            currency
	 * @return data as {@code String}.
	 */
	public String generatePaymentClientToken(String currency) {
		String clientToken = "";
		ClientTokenRequest clientTokenRequest = new ClientTokenRequest();
		JSONObject jsonData = new JSONObject();
		try {
			jsonData = new JSONObject(getBrainTreeDetails());
			privateKey = jsonData.getString("privateKey");
			publicKey = jsonData.getString("publicKey");
			merchantId = jsonData.getString("merchantId");
			environmentType = jsonData.getString("environment");
			merchantIdIndia = jsonData.getString("merchantIdIndia");
			merchantIdDefualt = jsonData.getString("merchantIdDefualt");
			if (environmentType.equals("SANDBOX")) {
				gateway = new BraintreeGateway(Environment.SANDBOX, merchantId, publicKey, privateKey);
			} else if (environmentType.equals("PRODUCTION")) {
				gateway = new BraintreeGateway(Environment.PRODUCTION, merchantId, publicKey, privateKey);
			}
			merchantIdForToken = getTokenByCuurency(currency, merchantIdIndia, merchantIdDefualt);
			clientTokenRequest.merchantAccountId(merchantIdForToken);
			clientToken = gateway.clientToken().generate(clientTokenRequest);
		} catch (Exception e) {
//			logger.error((e.getClass() + ":" + e.getMessage()) + " == method is generatePaymentClientToken");
		} finally {
			merchantIdForToken = null;
			privateKey = null;
			publicKey = null;
			merchantId = null;
			environmentType = null;
			merchantIdIndia = null;
			merchantIdDefualt = null;
			clientTokenRequest = null;
		}
		return clientToken;
	}

	private String getTokenByCuurency(String currency, String merchantIdIndia, String merchantIdDefualt) {
		String merchantAccountId = "";
		try {
			if (currency.equals("INR")) {
				merchantAccountId = merchantIdIndia;
			} else if (currency.equals("USD")) {
				merchantAccountId = merchantIdDefualt;
			}
		} catch (Exception e) {
//			logger.error((e.getClass() + ":" + e.getMessage()) + " == method is getTokenByCuurency");
		} finally {
			currency = null;
			merchantIdIndia = null;
			merchantIdDefualt = null;
		}
		return merchantAccountId;
	}

	/**
	 * Get Braintree details.
	 * 
	 * @return data as {@code String}.
	 */
	private String getBrainTreeDetails() {
		Connection connection = null;
		String result = "";
		PreparedStatement ps = null;
//		YujaaDataUtils yujaadetail = new YujaaDataUtils();
		String query = "SELECT `SETTINGS` FROM yujaa_settings WHERE `TYPE`= 'braintreeid';";
		try {
//			connection = yujaadetail.getConnection();
			ps = connection.prepareStatement(query);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				result = rs.getString(1);
			}
			rs.close();
		} catch (Exception e) {
//			logger.error((e.getClass() + ":" + e.getMessage()) + " == method is getBrainTreeDetails");
		} finally {
//			yujaadetail.closeResources(connection, ps);
//			yujaadetail = null;
			query = null;
		}
		return result;
	}

	/**
	 * For payment.
	 * 
	 * @param String
	 *            data
	 * 
	 * @return data as {@code JSONObject}.
	 */
	public JSONObject paymentProcess(String data) {
		JSONObject paymentDetails = new JSONObject();
		JSONObject userData = null;
		JSONObject invoiceData = null;
		TransactionRequest request = new TransactionRequest();
		try {
			if (data != null && !data.isEmpty()) {
				userData = new JSONObject(data);
				invoiceData = userData.getJSONObject("invoiceData");
				String price = invoiceData.get("finalPrice").toString();
				String amount = "" + Long.valueOf(Math.round((Float.parseFloat(price)) * 100 / 100));
				request.amount(new BigDecimal(amount));
				request.paymentMethodNonce(userData.get("payment_method_nonce").toString());
				request.options().submitForSettlement(true).threeDSecure().required(true).done();
				setMerchantIdInTransaction(request, invoiceData.get("currencyCode").toString());
				setCustomerInTransaction(request, data);
				paymentDetails = threeDVerification(request, data);
			}
		} catch (Exception e) {
//			logger.error((e.getClass() + ":" + e.getMessage()) + " == method is paymentProcess");
		} finally {
			userData = null;
			invoiceData = null;
			request = null;
		}
		return paymentDetails;
	}

	/**
	 * three D verification.
	 * 
	 * @param String
	 *            data
	 * 
	 * @param TransactionRequest
	 *            request
	 * @return transaction data as {@code JSON Object}.
	 */

	private JSONObject threeDVerification(TransactionRequest request, String data) {
		JSONObject userData = new JSONObject(data);
		JSONObject paymentDetails = new JSONObject();
		JSONObject threeDSecureInfo = new JSONObject();
		PaymentMethodNonce paymentMethodNonce = null;
		try {
			paymentDetails.put("isSuccess", "false");
			String nonce = userData.getString("payment_method_nonce");
			paymentMethodNonce = gateway.paymentMethodNonce().find(nonce);
			ThreeDSecureInfo info = paymentMethodNonce.getThreeDSecureInfo();
			if (info == null) {
				threeDSecureInfo.put("3D-Status", "false");
				return paymentDetails.put("3DSecureInfo", threeDSecureInfo);
			}
			info.getEnrolled();
			threeDSecureInfo.put("Enrolled", info.getEnrolled());
			threeDSecureInfo.put("isLiabilityShifted", info.isLiabilityShifted());
			threeDSecureInfo.put("isLiabilityShiftPossible", info.isLiabilityShiftPossible());
			threeDSecureInfo.put("3D-Status", info.getStatus());

			if (info.isLiabilityShifted()) {
				paymentDetails = executePaymentTransaction(request, data);
				paymentDetails.put("3DSecureInfo", threeDSecureInfo);
				String status = paymentDetails.get("isSuccess").toString();
				if (status.equalsIgnoreCase("true")) {
					paymentDetails.remove("isSuccess");
					paymentDetails.put("isSuccess", "true");
				} else {
					paymentDetails.put("isSuccess", "false");
				}
			}
		} catch (Exception e) {
			paymentDetails.put("paymentDetails", paymentDetails);
//			logger.error((e.getClass() + ":" + e.getMessage()) + " == method is threeDVerification");
		} finally {
			paymentMethodNonce = null;
			userData = null;
			threeDSecureInfo = null;
		}
		return paymentDetails;
	}

	/**
	 * Set Customer info.
	 * 
	 * @param String
	 *            data
	 * 
	 * @param TransactionRequest
	 *            request
	 */
	private void setCustomerInTransaction(TransactionRequest request, String data) {
		JSONObject customerData = null;
		CustomerRequest customerRequest = request.customer();
		try {
			if (data != null && !data.isEmpty()) {
				customerData = new JSONObject(data);
				customerRequest.email(customerData.getString("emailId"));
				customerRequest.firstName(customerData.getString("fullName"));
			}
		} catch (Exception e) {
//			logger.error((e.getClass() + ":" + e.getMessage()) + " == method is setCustomerInTransaction");
		} finally {
			customerData = null;
			customerRequest = null;
		}
	}

	/**
	 * For transaction.
	 * 
	 * @param String
	 *            data
	 * 
	 * @param TransactionRequest
	 *            request
	 * @return transaction data as {@code JSON Object}.
	 */
	private JSONObject executePaymentTransaction(TransactionRequest request, String data) {
		JSONObject paymentData = new JSONObject();
		Result<Transaction> result = null;
		try {
			result = gateway.transaction().sale(request);
			boolean isSuccess = result.isSuccess();
			if (isSuccess) {
				paymentData.put("isSuccess", "" + isSuccess);
				paymentData.put("message", "Payment Success");
				paymentData.put("paymentDataSet", this.getPaymentDetail(result, data));
			} else {
				paymentData.put("isSuccess", isSuccess);
				paymentData.put("message", result.getMessage());
				paymentData.put("paymentDataSet", this.getFailPaymentDetail(result, data));
			}
		} catch (Exception e) {
//			logger.error((e.getClass() + ":" + e.getMessage()) + " == method is executePaymentTransaction");
		} finally {
			result = null;
		}
		return paymentData;
	}

	/**
	 * Set merchant id by currency.
	 * 
	 * @param currency
	 *            Currency.
	 * @param TransactionRequest
	 *            request
	 */
	private void setMerchantIdInTransaction(TransactionRequest request, String currency) {
		String btDetails = getBrainTreeDetails();
		JSONObject jsonData = null;
		try {
			jsonData = new JSONObject(btDetails);
			if (currency.equalsIgnoreCase("INR")) {
				merchantIdIndia = jsonData.getString("merchantIdIndia");
				request.merchantAccountId(merchantIdIndia);
			} else {
				merchantIdDefualt = jsonData.getString("merchantIdDefualt");
				request.merchantAccountId(merchantIdDefualt);
			}
		} catch (Exception e) {
//			logger.error((e.getClass() + ":" + e.getMessage()) + " == method is executePaymentTransaction");
		} finally {
			btDetails = null;
			jsonData = null;
		}
	}

	/**
	 * get failed payment details.
	 * 
	 * @param String
	 *            data
	 * @param TransactionRequest
	 *            request
	 * @return Payment details data as {@code JSON Object}.
	 */
	private JSONObject getFailPaymentDetail(Result<Transaction> result, String data) {
		JSONObject paymentDataSet = new JSONObject();
		JSONObject jsonData = null;
		JSONObject invoiceData = null;
		SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy ");
		Calendar cal = Calendar.getInstance();
		try {
			String flag = "";
			jsonData = new JSONObject(data);
			invoiceData = jsonData.getJSONObject("invoiceData");
			String totalExDay = invoiceData.get("totalDays").toString();
			if (invoiceData.toString().contains("upgradeExistingPlan")) {
				flag = invoiceData.get("flag").toString();
			}
			int timePeriod = Integer.valueOf(totalExDay);
//			long time = Utils.getDateInTimeZone(new Date(System.currentTimeMillis()), "UTC").getTime();
			long satrtDate;
			long endDate;
			if (flag.equalsIgnoreCase("upgradeExistingPlan")) {
//				endDate = sd.getRenewExpiryTime(jsonData.getString("spacekey"));
				if (timePeriod >= 90) {
//					cal.setTimeInMillis(endDate);
					cal.add(Calendar.DATE, 1);
					satrtDate = cal.getTimeInMillis();
					cal.add(Calendar.DATE, timePeriod - 1);
				} else {
//					cal.setTimeInMillis(time);
					satrtDate = cal.getTimeInMillis();
					cal.add(Calendar.DATE, timePeriod - 1);
					endDate = cal.getTimeInMillis();
				}
			} else {
				satrtDate = cal.getTimeInMillis();
				cal.setTime(new Date());
				cal.add(Calendar.DATE, timePeriod - 1);
				cal.set(Calendar.MILLISECOND, 00);
				cal.set(Calendar.SECOND, 55);
				cal.set(Calendar.MINUTE, 59);
				cal.set(Calendar.HOUR, 11);
				endDate = cal.getTimeInMillis();
			}
			paymentDataSet.put("StartDate", sdf.format(satrtDate));
			paymentDataSet.put("paymentMadeOn", sdf.format(satrtDate));
//			paymentDataSet.put("endDate", sdf.format(endDate));
//			paymentDataSet.put("nextBillingDate", sdf.format(endDate));
//			paymentDataSet.put("paidThroughDate", sdf.format(endDate));
			paymentDataSet.put("quantity", 1);
		} catch (Exception e) {
//			logger.error((e.getClass() + ":" + e.getMessage()) + " == method is getFailPaymentDetail");
		} finally {
			jsonData = null;
			invoiceData = null;
			sdf = null;
//			service = null;
//			cal = null;
		}
		return paymentDataSet;

	}

	/**
	 * Get payment details form Braintree payment.
	 * 
	 * @param result
	 *            Braintree result.
	 * @param String
	 *            data
	 * @return Payment details data as {@code JSON Object}.
	 */
	private JSONObject getPaymentDetail(Result<Transaction> result, String data) {
		JSONObject paymentDataSet = new JSONObject();
		JSONObject jsonData = null;
		JSONObject invoiceData = null;
		SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy ");
//		YujaaPaymentServiceImpl service = new YujaaPaymentServiceImpl();
		Calendar cal = Calendar.getInstance();
		Transaction transaction = null;
		Date date = new Date(System.currentTimeMillis());
		try {
			String flag = "";
			jsonData = new JSONObject(data);
			invoiceData = jsonData.getJSONObject("invoiceData");
			if (invoiceData.toString().contains("upgradeExistingPlan")) {
				flag = invoiceData.get("flag").toString();
			}
			String totalExDay = invoiceData.get("totalDays").toString();
			int timePeriod = Integer.valueOf(totalExDay);
			long satrtDate;
			long endDate;
//			long time = Utils.getDateInTimeZone(date, "UTC").getTime();
			if (flag.equalsIgnoreCase("upgradeExistingPlan")) {
				endDate = 0l;
//				service.getRenewExpiryTime(jsonData.getString("spacekey"));
				if (timePeriod >= 90 && cal.getTimeInMillis() < endDate) {
					cal.setTimeInMillis(endDate);
					cal.add(Calendar.DATE, 1);
					satrtDate = cal.getTimeInMillis();
					cal.add(Calendar.DATE, timePeriod - 1);
					endDate = cal.getTimeInMillis();
				} else {
//					cal.setTimeInMillis(time);
					satrtDate = cal.getTimeInMillis();
					cal.add(Calendar.DATE, timePeriod - 1);
					endDate = cal.getTimeInMillis();
				}
			} else {
				satrtDate = cal.getTimeInMillis();
				cal.setTime(new Date());
				cal.add(Calendar.DATE, timePeriod - 1);
				cal.set(Calendar.MILLISECOND, 00);
				cal.set(Calendar.SECOND, 55);
				cal.set(Calendar.MINUTE, 59);
				cal.set(Calendar.HOUR, 11);
				endDate = cal.getTimeInMillis();
			}
			paymentDataSet.put("endDate", sdf.format(endDate));
			paymentDataSet.put("StartDate", sdf.format(satrtDate));
			paymentDataSet.put("nextBillingDate", sdf.format(endDate));
			paymentDataSet.put("paidThroughDate", sdf.format(endDate));
			paymentDataSet.put("quantity", 1);
			transaction = result.getTarget();
			paymentDataSet.put("amount", transaction.getAmount());
			paymentDataSet.put("currencyIsoCode", transaction.getCurrencyIsoCode());
			paymentDataSet.put("transactionId", transaction.getId());
			paymentDataSet.put("authorizationCode", transaction.getProcessorAuthorizationCode());
			paymentDataSet.put("responseCode", transaction.getProcessorResponseCode());
			paymentDataSet.put("cardType", transaction.getCreditCard().getCardType());
			paymentDataSet.put("lastDigit", transaction.getCreditCard().getLast4());
			paymentDataSet.put("email", transaction.getCustomer().getEmail());
			paymentDataSet.put("firstName", transaction.getCustomer().getFirstName());
			paymentDataSet.put("Status", transaction.getStatus().toString());
			paymentDataSet.put("responseText", transaction.getProcessorResponseText());
		} catch (Exception e) {
//			logger.error((e.getClass() + ":" + e.getMessage()) + " == method is getPaymentDetail");
		} finally {
			jsonData = null;
			invoiceData = null;
			sdf = null;
//			service = null;
			cal = null;
			date = null;
		}
		return paymentDataSet;
	}
}