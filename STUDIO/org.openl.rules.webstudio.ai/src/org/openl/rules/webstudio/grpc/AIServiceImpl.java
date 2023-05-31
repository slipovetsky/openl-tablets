package org.openl.rules.webstudio.grpc;

import javax.annotation.PreDestroy;

import org.openl.rules.webstudio.ai.WebstudioAIServiceGrpc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Component;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

@Component
public class AIServiceImpl implements AIService {
    private final String grpcAddress;
    private volatile ManagedChannel channel;
    private volatile WebstudioAIServiceGrpc.WebstudioAIServiceBlockingStub blockingStub;
    private volatile long lastGrpcCheckTime = 0L;

    @Autowired
    public AIServiceImpl(PropertyResolver environment) {
        this.grpcAddress = environment.getProperty("webstudio.ai.grpc");
    }

    @Override
    public boolean isEnabled() {
        boolean f = grpcAddress != null && grpcAddress.contains(":");
        if (f) {
            return getChannel() != null;
        }
        return false;
    }

    public ManagedChannel getChannel() {
        if (channel == null || channel.isTerminated() || blockingStub == null || System
            .currentTimeMillis() - lastGrpcCheckTime < 15000) {
            // Take the host and port from the environment variable
            String[] parts = grpcAddress.split(":");
            // Create a channel to connect to the server
            this.channel = ManagedChannelBuilder.forAddress(parts[0], Integer.parseInt(parts[1]))
                .usePlaintext()
                .build();
            try {
                this.lastGrpcCheckTime = System.currentTimeMillis();
                this.channel.getState(true); // Check if the channel is in a ready state
                this.blockingStub = WebstudioAIServiceGrpc.newBlockingStub(this.channel);
            } catch (StatusRuntimeException ignored) {
                this.channel = null;
                this.blockingStub = null;
            }
        }
        return channel;
    }

    @Override
    public WebstudioAIServiceGrpc.WebstudioAIServiceBlockingStub getBlockingStub() {
        return this.blockingStub;
    }

    @PreDestroy
    void destroy() {
        // Close the channel
        channel.shutdown();
    }

}
