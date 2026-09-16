package az.fitnest.user.client;

import az.fitnest.payment.grpc.GetCoinWalletRequest;
import az.fitnest.payment.grpc.GetCoinWalletResponse;
import az.fitnest.payment.grpc.PaymentServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentCoinGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentCoinGrpcClient.class);

    @GrpcClient("payment-backend")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub;

    @Value("${grpc.payment.deadline-ms:5000}")
    private long deadlineMs;

    public CoinWalletSnapshot getCoinWallet(Long userId) {
        GetCoinWalletRequest request = GetCoinWalletRequest.newBuilder()
                .setUserId(userId)
                .build();
        GetCoinWalletResponse response = paymentServiceStub
                .withDeadlineAfter(deadlineMs, TimeUnit.MILLISECONDS)
                .getCoinWallet(request);
        return CoinWalletSnapshot.from(response);
    }

    public record CoinWalletSnapshot(
            BigDecimal coinBalance,
            BigDecimal aznEquivalent,
            LocalDateTime validityDate
    ) {
        static CoinWalletSnapshot from(GetCoinWalletResponse response) {
            BigDecimal balance = parseDecimal(response.getCoinBalance());
            BigDecimal azn = parseDecimal(response.getAznEquivalent());
            LocalDateTime validity = null;
            if (response.getValidityDate() != null && !response.getValidityDate().isBlank()) {
                try {
                    validity = LocalDateTime.parse(response.getValidityDate());
                } catch (Exception e) {
                    log.warn("Failed to parse coin validity date '{}': {}", response.getValidityDate(), e.getMessage());
                }
            }
            return new CoinWalletSnapshot(balance, azn, validity);
        }

        private static BigDecimal parseDecimal(String value) {
            if (value == null || value.isBlank()) {
                return BigDecimal.ZERO;
            }
            try {
                return new BigDecimal(value);
            } catch (NumberFormatException e) {
                return BigDecimal.ZERO;
            }
        }

        public static CoinWalletSnapshot empty() {
            return new CoinWalletSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, null);
        }
    }
}
