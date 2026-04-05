package com.learn.aiintelligenttourism.tools;

import jakarta.annotation.Resource;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Value("${juhe.weather.key}")
    private String weatherKey;

    @Value("${juhe.exchange_rate.key}")
    private String exchangeRateKey;

    @Value("${amap.geocode.key}")
    private String amapGeocodeKey;

    @Resource(name = "knowledgeVectorStore")
    private VectorStore vectorStore;
    @Bean
    public ToolCallback[] allTools(){
        //FileOperationTool fileOperationTool = new FileOperationTool();
        //WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        //WebScrapingTool webScrapingTool = new WebScrapingTool();
        //ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        //TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        //PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        //TerminateTool terminateTool = new TerminateTool();
        JuheWeatherTool juheWeatherTool = new JuheWeatherTool(weatherKey);
        JuheExchangeRateTool juheExchangeRateTool = new JuheExchangeRateTool(exchangeRateKey);
        AmapTool amapTool = new AmapTool(amapGeocodeKey);
        PolicyAnnouncementTool policyAnnouncementTool = new PolicyAnnouncementTool();
        TourismKnowledgeTool tourismKnowledgeTool = new TourismKnowledgeTool(vectorStore);
        return ToolCallbacks.from(
                //fileOperationTool,
                //webSearchTool,
                //webScrapingTool,
                //resourceDownloadTool,
                //terminalOperationTool,
                //pdfGenerationTool,
                //terminateTool,
                tourismKnowledgeTool,
                juheWeatherTool,
                juheExchangeRateTool,
                amapTool,
                policyAnnouncementTool
        );
    }

    @Bean("policyTools")
    public ToolCallback[] policyTools() {
        AmapTool amapTool = new AmapTool(amapGeocodeKey);
        PolicyAnnouncementTool policyAnnouncementTool = new PolicyAnnouncementTool();
        return ToolCallbacks.from(
                policyAnnouncementTool,
                amapTool
        );
    }

    @Bean("simpleChatTools")
    public ToolCallback[] simpleChatTools() {
        AmapTool amapTool = new AmapTool(amapGeocodeKey);
        PolicyAnnouncementTool policyAnnouncementTool = new PolicyAnnouncementTool();
        JuheWeatherTool juheWeatherTool = new JuheWeatherTool(weatherKey);
        JuheExchangeRateTool juheExchangeRateTool = new JuheExchangeRateTool(exchangeRateKey);
        return ToolCallbacks.from(
                juheWeatherTool,
                juheExchangeRateTool,
                amapTool,
                policyAnnouncementTool
        );
    }


}
