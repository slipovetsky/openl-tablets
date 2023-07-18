package org.openl.rules.rest;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openl.rules.webstudio.ai.WebstudioAIServiceGrpc;
import org.openl.rules.webstudio.ai.WebstudioAi;
import org.openl.rules.webstudio.grpc.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.Hidden;

@RestController
@RequestMapping(value = "/assistant", produces = MediaType.APPLICATION_JSON_VALUE)
@Hidden
public class AssistantController {

    private final static String CHAT_TYPE_ASSISTANT = "ASSISTANT";
    private final static String CHAT_TYPE_USER = "USER";

    private final AIService aiService;

    @Autowired
    public AssistantController(AIService aiService) {
        this.aiService = aiService;
    }

    public static class Ref {
        String url;
        String title;

        @JsonCreator
        public Ref(@JsonProperty("url") String url, @JsonProperty("title") String title) {
            this.url = url;
            this.title = title;
        }

        public String getUrl() {
            return url;
        }

        public String getTitle() {
            return title;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    public static class Message {
        String type;
        String text;
        private List<Ref> refs;

        @JsonCreator
        public Message(@JsonProperty("text") String text,
                @JsonProperty("type") String type,
                @JsonProperty("refs") List<Ref> refs) {
            this.text = text;
            this.type = type;
            this.refs = refs;
        }

        public String getText() {
            return text;
        }

        public String getType() {
            return type;
        }

        public void setText(String text) {
            this.text = text;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<Ref> getRefs() {
            return refs;
        }

        public void setRefs(List<Ref> refs) {
            this.refs = refs;
        }
    }

    public static class MessageArrayWrapper {
        private List<Message> messages;

        @JsonCreator
        public MessageArrayWrapper(@JsonProperty("messages") List<Message> messages) {
            this.messages = messages;
        }

        public List<Message> getMessages() {
            return messages;
        }

        public void setMessages(List<Message> messages) {
            this.messages = messages;
        }
    }

    private static List<Ref> buildRefs(List<WebstudioAi.Ref> refs) {
        if (refs == null || refs.isEmpty()) {
            return Collections.emptyList();
        }
        return refs.stream().map(e -> new Ref(e.getUrl(), e.getTitle())).collect(Collectors.toList());
    }

    @PostMapping(value = "/ask_help")
    public Message[] askHelp(@RequestBody MessageArrayWrapper messageArrayWrapper) {
        Message[] messages = messageArrayWrapper.getMessages().toArray(new Message[0]);
        // get all messages except the last one are ignored
        Message[] history = new Message[messages.length - 1];
        if (history.length > 0) {
            System.arraycopy(messages, 0, history, 0, messages.length - 1);
        }
        Message lastMessage = messages[messages.length - 1];
        WebstudioAi.ChatRequest request = WebstudioAi.ChatRequest.newBuilder()
            .setChatType(WebstudioAi.ChatType.KNOWLEDGE)
            .setMessage(lastMessage.text)
            .addAllHistory(Stream.of(history)
                .map(e -> WebstudioAi.ChatMessage.newBuilder()
                    .setText(e.text)
                    .setType(CHAT_TYPE_USER.equals(e.getType()) ? WebstudioAi.MessageType.USER
                                                                : WebstudioAi.MessageType.ASSISTANT)
                    .build())
                .collect(Collectors.toList()))
            .build();
        WebstudioAIServiceGrpc.WebstudioAIServiceBlockingStub blockingStub = aiService.getBlockingStub();
        WebstudioAi.ChatReply response = blockingStub.chat(request);
        return response.getMessagesList()
            .stream()
            .map(e -> new Message(e.getText(), CHAT_TYPE_ASSISTANT, buildRefs(e.getRefsList())))
            .toArray(Message[]::new);
    }
}
