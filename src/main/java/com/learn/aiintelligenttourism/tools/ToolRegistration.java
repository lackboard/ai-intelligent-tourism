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

    @Resource
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
                juheExchangeRateTool
        );
    }


}
