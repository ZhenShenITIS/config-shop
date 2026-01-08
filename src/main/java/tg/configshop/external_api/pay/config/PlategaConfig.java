package tg.configshop.external_api.pay.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PlategaConfig {

    @Bean
    public RestClient plategaRestClient(@Value("${PAY_BASE_URL}") String apiUrl,
                                        @Value("${PAY_MERCHANT_ID}") String merchantId,
                                        @Value("${PAY_SECRET}") String apiToken)  {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(20))
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("X-MerchantId", merchantId)
                .defaultHeader("X-Secret", apiToken)
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(factory)
                .build();
    }
}
