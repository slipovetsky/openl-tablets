package org.openl.rules.ruleservice.kafka.publish;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.openl.rules.calc.SpreadsheetResultBeanPropertyNamingStrategy;
import org.openl.rules.project.model.RulesDeploy;
import org.openl.rules.project.model.RulesDeploy.PublisherType;
import org.openl.rules.ruleservice.core.ExceptionDetails;
import org.openl.rules.ruleservice.core.OpenLService;
import org.openl.rules.ruleservice.core.RuleServiceInstantiationException;
import org.openl.rules.ruleservice.core.ServiceInvocationAdvice;
import org.openl.rules.ruleservice.kafka.KafkaHeaders;
import org.openl.rules.ruleservice.kafka.RequestMessage;
import org.openl.rules.ruleservice.servlet.RuleServicesFilter;
import org.openl.rules.ruleservice.storelogdata.ObjectSerializer;
import org.openl.rules.ruleservice.storelogdata.StoreLogData;
import org.openl.rules.ruleservice.storelogdata.StoreLogDataException;
import org.openl.rules.ruleservice.storelogdata.StoreLogDataHolder;
import org.openl.rules.ruleservice.storelogdata.StoreLogDataManager;
import org.openl.rules.serialization.ProjectJacksonObjectMapperFactoryBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import org.slf4j.MDC;

public final class KafkaService implements Runnable {

    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors(),
        Runtime.getRuntime().availableProcessors() * 2,
        60L,
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>());

    private final Logger log = LoggerFactory.getLogger(KafkaService.class);

    private volatile boolean flag = true;
    private final OpenLService service;
    private final String requestIdHeaderKey;
    private final String inTopic;
    private final String outTopic;
    private final String dltTopic;
    private final Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();
    private final KafkaProducer<String, Object> producer;
    private final KafkaProducer<String, byte[]> dltProducer;
    private final KafkaConsumer<String, RequestMessage> consumer;
    private Thread loopRunningThread;
    private final ObjectSerializer objectSerializer;
    private final boolean storageEnabled;
    private StoreLogDataManager storeLogDataManager;
    private final SpreadsheetResultBeanPropertyNamingStrategy sprBeanPropertyNamingStrategy;

    public static KafkaService createService(OpenLService service,
            String requestIdHeaderKey,
            String inTopic,
            String outTopic,
            String dltTopic,
            KafkaConsumer<String, RequestMessage> consumer,
            KafkaProducer<String, Object> producer,
            KafkaProducer<String, byte[]> dltProducer,
            ObjectSerializer objectSerializer,
            StoreLogDataManager storeLogDataManager,
            boolean storeLogDataEnabled,
            RulesDeploy rulesDeploy) throws KafkaServiceException {
        return new KafkaService(service,
            requestIdHeaderKey,
            inTopic,
            outTopic,
            dltTopic,
            consumer,
            producer,
            dltProducer,
            objectSerializer,
            storeLogDataManager,
            storeLogDataEnabled,
            rulesDeploy);
    }

    private KafkaService(OpenLService service,
            String requestIdHeaderKey,
            String inTopic,
            String outTopic,
            String dltTopic,
            KafkaConsumer<String, RequestMessage> consumer,
            KafkaProducer<String, Object> producer,
            KafkaProducer<String, byte[]> dltProducer,
            ObjectSerializer objectSerializer,
            StoreLogDataManager storeLogDataManager,
            boolean storageEnabled,
            RulesDeploy rulesDeploy) throws KafkaServiceException {
        this.service = Objects.requireNonNull(service);
        this.requestIdHeaderKey = requestIdHeaderKey;
        this.inTopic = Objects.requireNonNull(inTopic);
        this.producer = Objects.requireNonNull(producer);
        this.consumer = Objects.requireNonNull(consumer);
        this.dltProducer = Objects.requireNonNull(dltProducer);
        this.objectSerializer = Objects.requireNonNull(objectSerializer);
        if (storageEnabled) {
            this.storeLogDataManager = Objects.requireNonNull(storeLogDataManager);
        }
        this.outTopic = outTopic;
        this.dltTopic = dltTopic;
        this.storageEnabled = storageEnabled;
        try {
            PropertyNamingStrategy propertyNamingStrategy = ProjectJacksonObjectMapperFactoryBean
                .extractPropertyNamingStrategy(rulesDeploy, service.getClassLoader());
            if (propertyNamingStrategy instanceof SpreadsheetResultBeanPropertyNamingStrategy) {
                this.sprBeanPropertyNamingStrategy = (SpreadsheetResultBeanPropertyNamingStrategy) propertyNamingStrategy;
            } else {
                this.sprBeanPropertyNamingStrategy = null;
            }
        } catch (RuleServiceInstantiationException e) {
            throw new KafkaServiceException("Failed to initialize 'PropertyNamingStrategy' for kafka service.", e);
        }
    }

    public boolean isStoreLogDataEnabled() {
        return storageEnabled;
    }

    public StoreLogDataManager getStoreLogDataManager() {
        return storeLogDataManager;
    }

    public OpenLService getService() {
        return service;
    }

    public String getInTopic() {
        return inTopic;
    }

    public String getOutTopic(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(KafkaHeaders.REPLY_TOPIC);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return outTopic;
    }

    public String getDltTopic(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(KafkaHeaders.REPLY_DLT_TOPIC);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        header = record.headers().lastHeader(KafkaHeaders.REPLY_TOPIC);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return dltTopic;
    }

    private void subscribeConsumer() {
        consumer.subscribe(Collections.singletonList(getInTopic()), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                if (log.isInfoEnabled()) {
                    log.info("Lost partitions in rebalance. Committing current offsets: {}", currentOffsets);
                }
                consumer.commitSync(currentOffsets);
                currentOffsets.clear();
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            }
        });
    }

    private void initialize() {
        loopRunningThread = new Thread(this);
        subscribeConsumer();
    }

    private void runLoop() {
        loopRunningThread.start();
    }

    public void start() throws KafkaServiceException {
        try {
            initialize();
            runLoop();
        } catch (Exception e) {
            throw new KafkaServiceException("Failed to start kafka service.", e);
        }
    }

    public ObjectSerializer getObjectSerializer() {
        return objectSerializer;
    }

    @Override
    public void run() {
        while (flag) {
            try {
                ConsumerRecords<String, RequestMessage> records = consumer.poll(Duration.ofMillis(100));
                if (!records.isEmpty()) {
                    CountDownLatch countDownLatch = new CountDownLatch(records.count());
                    ZonedDateTime incomingTime = ZonedDateTime.now();
                    for (ConsumerRecord<String, RequestMessage> consumerRecord : records) {
                        executor.submit(() -> {
                            StoreLogData storeLogData = isStoreLogDataEnabled() ? StoreLogDataHolder.get() : null;
                            String requestIdHeader = null;
                            try {
                                if (requestIdHeaderKey != null) {
                                    var idHeader = consumerRecord.headers().lastHeader(requestIdHeaderKey);
                                    if (idHeader != null) {
                                        requestIdHeader = new String(idHeader.value(), StandardCharsets.UTF_8);
                                    }
                                    if (StringUtils.isBlank(requestIdHeader)) {
                                        requestIdHeader = UUID.randomUUID().toString();
                                    }
                                    MDC.put(RuleServicesFilter.REQUEST_ID_KEY, requestIdHeader);
                                }
                                if (storeLogData != null) {
                                    storeLogData.setServiceClass(service.getServiceClass());
                                    storeLogData.setServiceName(service.getName());
                                    storeLogData.setIncomingMessageTime(incomingTime);
                                    storeLogData.setPublisherType(PublisherType.KAFKA);
                                    storeLogData.setObjectSerializer(getObjectSerializer());
                                    storeLogData.setConsumerRecord(consumerRecord);
                                }
                                RequestMessage requestMessage = consumerRecord.value();
                                if (storeLogData != null) {
                                    storeLogData.setServiceMethod(requestMessage.getMethod());
                                    storeLogData.setParameters(requestMessage.getParameters());
                                }
                                String outputTopic = getOutTopic(consumerRecord);
                                if (!StringUtils.isBlank(outputTopic)) {
                                    Object result = requestMessage.getMethod()
                                        .invoke(service.getServiceBean(), requestMessage.getParameters());
                                    Header header = consumerRecord.headers().lastHeader(KafkaHeaders.REPLY_PARTITION);
                                    ProducerRecord<String, Object> producerRecord;
                                    if (header == null) {
                                        producerRecord = new ProducerRecord<>(outputTopic,
                                            consumerRecord.key(),
                                            result);
                                    } else {
                                        Integer partition = Integer
                                            .parseInt(new String(header.value(), StandardCharsets.UTF_8));
                                        producerRecord = new ProducerRecord<>(outputTopic,
                                            partition,
                                            consumerRecord.key(),
                                            result);
                                    }
                                    if (requestIdHeader != null) {
                                        producerRecord.headers().add(requestIdHeaderKey, requestIdHeader.getBytes(StandardCharsets.UTF_8));
                                    }
                                    forwardHeadersToOutput(consumerRecord, producerRecord);

                                    if (storeLogData != null) {
                                        storeLogData.setOutcomingMessageTime(ZonedDateTime.now());
                                    }
                                    String finalRequestIdHeader = requestIdHeader;
                                    producer.send(producerRecord, (metadata, exception) -> {
                                        if (storeLogData != null) {
                                            storeLogData.setProducerRecord(producerRecord);
                                        }
                                        if (exception == null && storeLogData != null) {
                                            try {
                                                getStoreLogDataManager().store(storeLogData);
                                            } catch (StoreLogDataException e) {
                                                exception = e;
                                            }
                                        }
                                        if (exception != null) {
                                            try {
                                                if (log.isErrorEnabled()) {
                                                    log.error(
                                                        "Failed to send a result message for method '{}' in service '{}' to output topic '{}'.",
                                                        requestMessage.getMethod(),
                                                        getService().getDeployPath(),
                                                        getOutTopic(consumerRecord), exception);
                                                }
                                            } catch (Exception e) {
                                                log.error("Unexpected error.", e);
                                            }
                                            sendErrorToDlt(consumerRecord, exception, storeLogData, finalRequestIdHeader);
                                        }
                                    });
                                } else {
                                    if (storeLogData != null) {
                                        storeLogData.setOutcomingMessageTime(ZonedDateTime.now());
                                        getStoreLogDataManager().store(storeLogData);
                                    }
                                }
                            } catch (InvocationTargetException | UndeclaredThrowableException e) {
                                Throwable ex = e.getCause();
                                sendError(consumerRecord, storeLogData, ex instanceof Exception ?  (Exception) ex : e, requestIdHeader);
                            } catch (Exception e) {
                                sendError(consumerRecord, storeLogData, e, requestIdHeader);
                            } finally {
                                countDownLatch.countDown();
                                if (isStoreLogDataEnabled()) {
                                    StoreLogDataHolder.remove();
                                }
                                if (requestIdHeader != null) {
                                    MDC.remove(RuleServicesFilter.REQUEST_ID_KEY);
                                }
                            }
                        });
                    }
                    countDownLatch.await();
                    for (ConsumerRecord<String, RequestMessage> record : records) {
                        currentOffsets.put(new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset() + 1));
                    }
                    try {
                        consumer.commitSync(currentOffsets);
                        if (log.isDebugEnabled()) {
                            log.debug("Current offsets have been committed: {}", currentOffsets);
                        }
                    } catch (Exception e) {
                        if (log.isErrorEnabled()) {
                            log.error("Failed to commit current offsets: {}", currentOffsets);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Something wrong.", e);
            }
        }
    }

    private void sendError(ConsumerRecord<String, RequestMessage> consumerRecord, StoreLogData storeLogData, Exception e, String requestIdHeader) {
        if (log.isErrorEnabled()) {
            log.error("Failed to process a message from input topic '{}'.", getInTopic(), e);
        }
        sendErrorToDlt(consumerRecord, e, storeLogData, requestIdHeader);
    }

    private void forwardHeadersToDlt(ConsumerRecord<?, ?> originalRecord, ProducerRecord<?, ?> record) {
        for (Header header : originalRecord.headers()) {
            record.headers().add(header);
        }
    }

    private void forwardHeadersToOutput(ConsumerRecord<?, ?> originalRecord, ProducerRecord<?, ?> record) {
        for (Header header : originalRecord.headers().headers(KafkaHeaders.CORRELATION_ID)) {
            record.headers().add(header);
        }
    }

    private void setDltHeaders(ConsumerRecord<String, RequestMessage> record,
            Exception e,
            ProducerRecord<?, ?> dltRecord) {
        dltRecord.headers()
            .add(KafkaHeaders.DLT_ORIGINAL_MESSAGE_KEY,
                record.key() == null ? null : record.key().getBytes(StandardCharsets.UTF_8));
        dltRecord.headers()
            .add(KafkaHeaders.DLT_ORIGINAL_PARTITION,
                String.valueOf(record.partition()).getBytes(StandardCharsets.UTF_8));
        dltRecord.headers()
            .add(KafkaHeaders.DLT_ORIGINAL_OFFSET, String.valueOf(record.offset()).getBytes(StandardCharsets.UTF_8));
        dltRecord.headers().add(KafkaHeaders.DLT_ORIGINAL_TOPIC, record.topic().getBytes(StandardCharsets.UTF_8));

        setDltHeadersForException(dltRecord, record.value().getException());

        setDltHeadersForException(dltRecord, e);

        if (record.key() != null) {
            dltRecord.headers()
                .add(KafkaHeaders.DLT_ORIGINAL_MESSAGE_KEY, record.key().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void setDltHeadersForException(ProducerRecord<?, ?> dltRecord, Exception exception) {
        if (exception != null) {
            dltRecord.headers()
                .add(KafkaHeaders.DLT_EXCEPTION_FQCN, exception.getClass().getName().getBytes(StandardCharsets.UTF_8));
            ExceptionDetails details = ServiceInvocationAdvice
                .getExceptionDetailAndType(exception, sprBeanPropertyNamingStrategy)
                .getRight();
            dltRecord.headers()
                .add(KafkaHeaders.DLT_EXCEPTION_MESSAGE,
                    Optional.ofNullable(details.getMessage())
                        .map(s -> s.getBytes(StandardCharsets.UTF_8))
                        .orElse(null));
            dltRecord.headers()
                .add(KafkaHeaders.DLT_EXCEPTION_STACKTRACE,
                    ExceptionUtils.getStackTrace(exception).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void sendErrorToDlt(ConsumerRecord<String, RequestMessage> record, Exception e, StoreLogData storeLogData, String requestIdHeader) {
        final String dltTopic = getDltTopic(record);
        if (StringUtils.isEmpty(dltTopic)) {
            return;
        }
        try {
            if (requestIdHeader != null) {
                record.headers().add(requestIdHeaderKey, requestIdHeader.getBytes(StandardCharsets.UTF_8));
            }
            ProducerRecord<String, byte[]> dltRecord;
            Header header = record.headers().lastHeader(KafkaHeaders.REPLY_DLT_PARTITION);
            if (header == null) {
                dltRecord = new ProducerRecord<>(dltTopic, record.key(), record.value().getRawData());
            } else {
                Integer partition = Integer.parseInt(new String(header.value(), StandardCharsets.UTF_8));
                dltRecord = new ProducerRecord<>(dltTopic, partition, record.key(), record.value().getRawData());
            }
            forwardHeadersToDlt(record, dltRecord);
            setDltHeaders(record, e, dltRecord);
            if (storeLogData != null) {
                storeLogData.setOutcomingMessageTime(ZonedDateTime.now());
            }
            dltProducer.send(dltRecord, (metadata, exception) -> {
                if (storeLogData != null) {
                    storeLogData.setDltRecord(dltRecord);
                    storeLogData.fault();
                }
                if (exception != null && log.isErrorEnabled()) {
                    log.error(String.format("Failed to send a message to dead letter queue topic '%s'.%sPayload: %s",
                        dltTopic,
                        System.lineSeparator(),
                        record.value().asText()), exception);
                } else if (storeLogData != null) {
                    try {
                        getStoreLogDataManager().store(storeLogData);
                    } catch (StoreLogDataException e1) {
                        log.error("Failed on data store operation.", e1);
                    }
                }
            });
        } catch (Exception e1) {
            if (log.isErrorEnabled()) {
                log.error(String.format("Failed to send a message to dead letter queue topic '%s'.%sPayload: %s",
                    dltTopic,
                    System.lineSeparator(),
                    record.value().asText()), e1);
            }
        }
    }

    public void stop() throws InterruptedException {
        flag = false;
        loopRunningThread.join();
    }
}
