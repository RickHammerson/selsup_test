package ru.work.selsup;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class CrptApi extends SomeApi {
    public CrptApi(Integer requestsLimit, TimeUnit timeUnit) {
        super(requestsLimit, timeUnit);
    }
    
    /**
     * Решил, что подпись - это ЭП или ЭЦП — нужна для работы в информационной системе «Честный ЗНАК»
     * https://ca.kontur.ru/articles/25328-elektronnaya_podpis_dlya_chestnogo_znaka_komu_nuzhna_gde_poluchit_kak_ispolzovat
     * Но это надо указывать в тестовом задании
     * <p>
     * Решил использовать @SneakyThrows. Обработка всех ошибок в рантайме, чтобы не падал метод Обработать можно как
     * хотите
     *
     * @param document  Документ для отправки
     * @param signature Подпись
     */
    @SneakyThrows
    public void createDocument(Document document, String signature) {
        semaphore.acquire();
        Request request = getDocumentCreateRequest(document, signature);
        log.info(
            "Thread {} creating document. Count of running threads: {}}",
            Thread.currentThread().getName(),
            counter.incrementAndGet()
        );
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Request isn't successful " + response);
            }
            // Обработка ответа. Там уже делаем с классом, что хотим
            objectMapper.readValue(response.body().string(), SomeResponse.class);
        } finally {
            log.info(
                "releasing thread {}. Count of running threads: {}",
                Thread.currentThread().getName(),
                counter.decrementAndGet()
            );
            semaphore.release();
        }
    }
    
    /**
     * Создание HTTP запроса из документа и подписи
     */
    private Request getDocumentCreateRequest(Document document, String signature) throws JsonProcessingException {
        RequestBody body = RequestBody.create(
            objectMapper.writeValueAsString(document),
            MediaType.parse("application/json")
        );
        
        return new Request.Builder().url(apiProperties.getCreateUrl())
            .post(body)
            .addHeader("Signature", signature)
            .build();
    }
    
    @SneakyThrows
    public static void main(String[] args) {
        CrptApi api = new CrptApi(5, TimeUnit.MINUTES);
        Document document = new Document();
        // ...заполнение объекта document данными
        //Всё остальное для теста, чтобы не пришлось ничего дополнительно писать для проверки кода
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        for (int i = 0; i < 20; i++) {
            executorService.execute(() -> api.createDocument(document, "The best signature for chestniy Znak"));
        }
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.MINUTES);
    }
}

/**
 * Абстрактный класс для реализации любого API с лимитированным количеством запросов
 *
 * Заполнение зависимостей должно быть через аннотацию ломбока @RequiredArgsConstructor с помощью Spring Сейчас просто
 * создаю ручками
 */
@Data
abstract class SomeApi {
    protected final ApiProperties apiProperties = new ApiProperties();
    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected final OkHttpClient client = new OkHttpClient();
    protected final Semaphore semaphore;
    protected final AtomicInteger counter = new AtomicInteger();
    protected final int requestLimit;
    
    public SomeApi(Integer requestLimit, TimeUnit timeUnit) {
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit);
        createSemaphoreResetScheduler(timeUnit);
    }
    
    private void createSemaphoreResetScheduler(TimeUnit timeUnit) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::releasePermits, 0, 1, timeUnit);
    }
    
    private void releasePermits() {
        semaphore.drainPermits();
        semaphore.release(requestLimit);
    }
}

/**
 * Заполняется снаружи с переменных окружения, чтобы не хардкодить необходимые проперти Сейчас специально заполнил сам
 */
@Data
class ApiProperties {
    private final String createUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    //Другие URLки
}

/**
 * Класс ответа от API
 */
@Data
class SomeResponse {
    private List<String> listField;
    private String stringFiled;
}

/**
 * Валидацию полей можно сделать через аннотации jakarta + Spring
 */
@Data
class Document {
    private Description description;
    @JsonProperty("doc_id")
    private String docId;
    @JsonProperty("doc_status")
    private String docStatus;
    @JsonProperty("doc_type")
    private DocType docType;
    private boolean importRequest;
    @JsonProperty("owner_inn")
    private String ownerInn;
    @JsonProperty("participant_inn")
    private String participantInn;
    @JsonProperty("producerInn")
    private String producer_inn;
    @JsonProperty("production_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate productionDate;
    @JsonProperty("production_type")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate productionType;
    private List<Product> products;
    @JsonProperty("regDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate reg_date;
    @JsonProperty("reg_number")
    private String regNumber;
}

enum DocType {
    LP_INTRODUCE_GOODS
}

@Data
class Product {
    @JsonProperty("certificate_document")
    private String certificateDocument;
    @JsonProperty("certificate_document_date")
    private String certificateDocumentDate;
    @JsonProperty("certificate_document_number")
    private String certificateDocumentNumber;
    @JsonProperty("owner_inn")
    private String ownerInn;
    @JsonProperty("producer_inn")
    private String producerInn;
    @JsonProperty("production_date")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate productionDate;
    @JsonProperty("tnved_code")
    private String tnvedCode;
    @JsonProperty("uit_code")
    private String uitCode;
    @JsonProperty("uitu_code")
    private String uituCode;
}

@Data
class Description {
    private String participantInn;
}
