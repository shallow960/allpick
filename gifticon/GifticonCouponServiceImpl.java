package com.allpick.new_allpick.gifticon.application;

import com.allpick.new_allpick.gifticon.domain.entity.GifticonCoupon;
import com.allpick.new_allpick.gifticon.domain.entity.GifticonProduct;
import com.allpick.new_allpick.gifticon.domain.entity.GifticonTransactionLog;
import com.allpick.new_allpick.gifticon.domain.enums.TransactionStatus;
import com.allpick.new_allpick.gifticon.domain.exception.GifticonErrorCode;
import com.allpick.new_allpick.gifticon.domain.exception.GifticonException;
import com.allpick.new_allpick.gifticon.domain.repository.GifticonCouponRepository;
import com.allpick.new_allpick.gifticon.infrastructure.GiftishowApiClient;
import com.allpick.new_allpick.point.application.PointRewardService;
import com.allpick.new_allpick.point.presentation.dto.PointRewardResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * [포트폴리오 발췌] 기프티콘 구매 — 트랜잭션 경계 설계가 핵심.
 *
 * 구매 전체를 하나의 @Transactional로 묶어, 외부 API 실패 시 다이아 차감이 롤백으로
 * 자동 복구되게 한다. 단, 실패 거래 로그는 롤백에 휩쓸리면 안 되므로 GifticonTxLogWriter를
 * 통해 REQUIRES_NEW(독립 트랜잭션)로 따로 커밋한다.
 *
 * (쿠폰함 조회 / 상태 동기화(0201) / 만료 배치 등은 발췌에서 생략)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GifticonCouponServiceImpl implements GifticonCouponService {

    private final GifticonCouponRepository couponRepository;
    private final GifticonTxLogWriter txLogWriter;          // 실패 로그 독립 커밋용
    private final GifticonProductService productService;
    private final GiftishowApiClient giftishowApiClient;
    private final PointRewardService pointRewardService;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long DIAMOND_RATE = 10;            // 10 다이아 = 1원

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public GifticonCoupon purchaseGifticon(UUID uid, String goodsCode, String phoneNo) {
        // 1) 상품 조회 + 판매 가능 여부 (가격은 프론트가 아닌 DB 기준)
        GifticonProduct product = productService.getProductByGoodsCode(goodsCode);
        if (!product.isPurchasable()) {
            throw new GifticonException(GifticonErrorCode.PRODUCT_NOT_ON_SALE);
        }

        long diamondAmount = product.getSalePrice() * DIAMOND_RATE;
        String trId = generateTrId();

        // 2) 다이아 차감 (잔액 부족 시 예외 → 트랜잭션 롤백)
        PointRewardResult spendResult;
        try {
            spendResult = pointRewardService.spendGifticonPurchase(uid, trId, diamondAmount);
        } catch (Exception e) {
            throw new GifticonException(GifticonErrorCode.INSUFFICIENT_DIAMONDS, e.getMessage(), e);
        }

        GifticonCoupon coupon = GifticonCoupon.issue(uid, product, trId, diamondAmount);

        // 3) 기프티쇼 API 쿠폰 발송 → 성공/실패 분기
        try {
            Map<String, Object> apiResponse = giftishowApiClient.sendCoupon(goodsCode, trId, phoneNo);
            String code = (String) apiResponse.get("code");

            if ("0000".equals(code)) {
                // ── 성공 → PIN/바코드 반영 후 쿠폰 + 성공 로그 저장 (메인 트랜잭션) ──
                Map<String, Object> result = (Map<String, Object>) apiResponse.get("result");
                coupon.applyApiResponse(
                        (String) result.get("orderNo"), (String) result.get("pinNo"),
                        (String) result.get("couponImgUrl"), null, null);
                couponRepository.save(coupon);
                product.incrementPurchaseCount();

                GifticonTransactionLog txLog = GifticonTransactionLog.success(
                        uid, coupon.getId(), trId, product, diamondAmount, null, null,
                        code, (String) apiResponse.get("message"));
                couponRepository.flush();
                txLogWriter.saveIndependently(txLog);
                return coupon;
            }

            // ── API 실패 → 실패 로그만 독립 커밋, 메인 트랜잭션은 롤백돼 다이아 자동 복구 ──
            GifticonTransactionLog txLog = GifticonTransactionLog.fail(
                    uid, trId, product, diamondAmount, TransactionStatus.FAILED,
                    code, (String) apiResponse.get("message"), null);
            txLogWriter.saveIndependently(txLog);
            throw new GifticonException(GifticonErrorCode.COUPON_ISSUE_FAILED, "API 응답 코드: " + code);

        } catch (GifticonException e) {
            throw e;
        } catch (Exception e) {
            // ── 통신 실패/타임아웃 → 실패 로그 독립 커밋, 다이아는 롤백으로 복구 ──
            GifticonTransactionLog txLog = GifticonTransactionLog.fail(
                    uid, trId, product, diamondAmount, TransactionStatus.TIMEOUT,
                    null, null, e.getMessage());
            txLogWriter.saveIndependently(txLog);
            throw new GifticonException(GifticonErrorCode.API_CALL_FAILED, e.getMessage(), e);
        }
    }

    /** 거래 ID 생성 (allpick_yyyyMMdd_xxxxxxxx, 25자 이하 Unique — 기프티쇼 규격) */
    private String generateTrId() {
        String date = LocalDate.now(KST).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "allpick_" + date + "_" + random;
    }
}
