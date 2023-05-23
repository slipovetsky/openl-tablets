package org.openl.rules.rest;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpSession;

import org.openl.rules.lang.xls.syntax.TableSyntaxNode;
import org.openl.rules.method.ExecutableRulesMethod;
import org.openl.rules.project.ai.OpenL2TextUtils;
import org.openl.rules.ui.WebStudio;
import org.openl.rules.webstudio.ai.WebstudioAIServiceGrpc;
import org.openl.rules.webstudio.ai.WebstudioAi;
import org.openl.rules.webstudio.web.util.WebStudioUtils;
import org.openl.types.IOpenClass;
import org.openl.types.IOpenMethod;
import org.openl.types.IOpenMethodHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.PropertyResolver;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.swagger.v3.oas.annotations.Hidden;

@RestController
@RequestMapping(value = "/tableEditor", produces = MediaType.APPLICATION_JSON_VALUE)
@Hidden
public class TableEditorExtrasController {

    private static final boolean REPLACE_ALIASES_WITH_BASE_TYPES = true;

    private final String grpcAddress;
    private ManagedChannel channel;
    private WebstudioAIServiceGrpc.WebstudioAIServiceBlockingStub blockingStub;

    @Autowired
    public TableEditorExtrasController(PropertyResolver environment) {
        this.grpcAddress = environment.getProperty("webstudio.ai.grpc");
    }

    private Set<String> getAITypeahead(TableSyntaxNode tableSyntaxNode, int row, int col, String text) {
        Set<String> completions = new HashSet<>();
        if (grpcAddress != null) {
            if (channel == null || channel.isTerminated()) {
                // Take the host and port from the environment variable
                String[] parts = grpcAddress.split(":");
                // Create a channel to connect to the server
                this.channel = ManagedChannelBuilder.forAddress(parts[0], Integer.parseInt(parts[1]))
                    .usePlaintext()
                    .build();
                // Create a client stub, it is not thread-safe
                blockingStub = WebstudioAIServiceGrpc.newBlockingStub(channel);
            }
            // Build the request tableRefTypes
            Set<IOpenClass> types = new HashSet<>();
            Set<IOpenMethod> methodRefs = OpenL2TextUtils
                .methodRefs((ExecutableRulesMethod) tableSyntaxNode.getMember());
            for (IOpenClass type : OpenL2TextUtils.methodTypes((ExecutableRulesMethod) tableSyntaxNode.getMember())) {
                OpenL2TextUtils.collectTypes(type, types, 1, REPLACE_ALIASES_WITH_BASE_TYPES);
            }
            for (IOpenMethod method : methodRefs) {
                OpenL2TextUtils.collectTypes(method.getType(), types, 1, REPLACE_ALIASES_WITH_BASE_TYPES);
            }
            final String refTypes = types.stream()
                .map(e -> OpenL2TextUtils.openClassToString(e, REPLACE_ALIASES_WITH_BASE_TYPES))
                .collect(Collectors.joining("/n/n"));
            // Build the request tableRefMethods
            final String refMethods = methodRefs.stream()
                .map(e -> OpenL2TextUtils.methodHeaderToString(e, REPLACE_ALIASES_WITH_BASE_TYPES))
                .collect(Collectors.joining(" {}/n"));
            final String table = OpenL2TextUtils.methodToString((ExecutableRulesMethod) tableSyntaxNode.getMember(),
                REPLACE_ALIASES_WITH_BASE_TYPES,
                true,
                false);

            // Send a gRPC request and handle the response
            WebstudioAi.TypeaheadRequest request = WebstudioAi.TypeaheadRequest.newBuilder()
                .setTable(table)
                .setRefMethods(refMethods)
                .setRefTypes(refTypes)
                .setFormula(text)
                .build();
            WebstudioAi.TypeaheadReply response = blockingStub.typeahead(request);
            for (int i = 0; i < response.getCompletionsCount(); i++) {
                completions.add(text + response.getCompletions(i));
            }
        }
        return completions;
    }

    private Set<String> getClassicTypeahead(TableSyntaxNode tableSyntaxNode, int row, int col, String text) {
        Set<String> vars = new HashSet<>();
        if (tableSyntaxNode.getMember() instanceof IOpenMethodHeader) {
            IOpenMethodHeader openMethodHeader = (IOpenMethodHeader) tableSyntaxNode.getMember();
            for (int i = 0; i < openMethodHeader.getSignature().getNumberOfParameters(); i++) {
                vars.add(openMethodHeader.getSignature().getParameterName(i));
            }
        }
        return vars;
    }

    @GetMapping(value = "/typeahead")
    public String[] typeahead(HttpSession httpSession, int row, int col, String text) {
        byte[] decodedBytes = Base64.getDecoder().decode(text);
        text = new String(decodedBytes);

        WebStudio studio = WebStudioUtils.getWebStudio(httpSession);
        String tableUri = studio.getTableUri();
        Set<String> vars = new HashSet<>();
        if (tableUri != null) {
            TableSyntaxNode tableSyntaxNode = studio.getModel().findNode(tableUri);
            Set<String> classicTypeahead = getClassicTypeahead(tableSyntaxNode, row, col, text);
            Set<String> aiTypeahead = getAITypeahead(tableSyntaxNode, row, col, text);
            Set<String> result = new HashSet<>();
            result.addAll(classicTypeahead);
            result.addAll(aiTypeahead);
            return result.toArray(new String[0]);
        }
        return new String[] {};
    }

    @PreDestroy
    void destroy() {
        // Close the channel
        channel.shutdown();
    }
}
