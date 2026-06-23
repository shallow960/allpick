package com.allpick.new_allpick.gifticon.application;

import com.allpick.new_allpick.gifticon.domain.entity.GifticonTransactionLog;
import com.allpick.new_allpick.gifticon.domain.repository.GifticonTransactionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * [포트폴리오 발췌] 기프티콘 거래 로그를 독립 트랜잭션으로 기록.
 *
 * 구매 실패 시 메인 트랜잭션은 롤백돼야 하지만(다이아 차감 복구), 실패 로그는 남아야 한다.
 * 같은 클래스 내부 호출은 Spring AOP 프록시를 거치지 않아 REQUIRES_NEW가 적용되지 않으므로
 * 로그 저장을 별도 빈으로 분리했다.
 */
@Component
@RequiredArgsConstructor
public class GifticonTxLogWriter {

    private final GifticonTransactionLogRepository txLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveIndependently(GifticonTransactionLog log) {
        txLogRepository.save(log);  // 메인 트랜잭션이 롤백돼도 이 로그는 독립 커밋됨
    }
}
