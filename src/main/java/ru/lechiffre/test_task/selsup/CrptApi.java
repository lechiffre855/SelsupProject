package ru.lechiffre.test_task.selsup;

import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.List;
import java.util.concurrent.*;

@RestController
public class CrptApi {
    TimeUnit timeUnit;
    int requestLimit;


    public CrptApi(TimeUnit timeUnit, int requestLimit){
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
    }
    private final RateLimiter rateLimiter = new RateLimiter(requestLimit, 1, timeUnit);

    @PostMapping("/create")
    public synchronized ResponseEntity<String> postCreateDocument(@RequestBody Document document, String sign) {
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Request interrupted");
        }
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Document> request = new HttpEntity<>(document, headers);

        restTemplate.postForEntity(url, request, Object.class);
        return new ResponseEntity<>(HttpStatus.OK);
    }
    @PreDestroy
    public void onDestroy(){
        rateLimiter.stop();
    }

    @Setter
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private Date productionDate;
        private String productionType;
        private List<Product> products;
        private Date regDate;
        private String regNumber;

        @Getter
        @Setter
        @AllArgsConstructor
        public static class Description {
            private String participantInn;
        }
        @Setter
        @Getter
        @AllArgsConstructor
        public static class Product {
            private String certificateDocument;
            private Date certificateDocumentDate;
            private String certificateDocumentNumber;
            private String ownerInn;
            private String producerInn;
            private Date productionDate;
            private String tnvedCode;
            private String uitCode;
            private String uituCode;
        }
    }
    private class RateLimiter {
        private final Semaphore semaphore;
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        public RateLimiter(int maxRequestsPerPeriod, long period, TimeUnit timeUnit) {
            semaphore = new Semaphore(maxRequestsPerPeriod);

            scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    int permitsToRelease = maxRequestsPerPeriod - semaphore.availablePermits();
                    if (permitsToRelease > 0) {
                        semaphore.release(permitsToRelease);
                    }
                }
            }, 0, period, timeUnit);
        }
        public void acquire() throws InterruptedException {
            semaphore.acquire();
        }
        public void stop() {
            scheduler.shutdownNow();
        }
    }

}
