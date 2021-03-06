package com.ewolff.microservice.invoicing.poller;

import java.util.Date;

import org.apache.http.client.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.ewolff.microservice.invoicing.Invoice;
import com.ewolff.microservice.invoicing.InvoiceRepository;
import com.rometools.rome.feed.atom.Entry;
import com.rometools.rome.feed.atom.Feed;

@Component
public class OrderPoller {

	private final Logger log = LoggerFactory.getLogger(OrderPoller.class);

	private String url = "";

	private RestTemplate restTemplate = new RestTemplate();

	private Date lastModified = null;

	private InvoiceRepository orderRepository;

	@Autowired
	public OrderPoller(@Value("${order.url}") String url, InvoiceRepository orderRepository) {
		super();
		this.url = url;
		this.orderRepository = orderRepository;
	}

	@Scheduled(fixedDelay = 30000)
	public void poll() {
		HttpHeaders requestHeaders = new HttpHeaders();
		if (lastModified != null) {
			requestHeaders.set("If-Modified-Since", DateUtils.formatDate(lastModified));
		}
		HttpEntity<?> requestEntity = new HttpEntity(requestHeaders);
		ResponseEntity<Feed> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, Feed.class);

		if (response.getStatusCode() != HttpStatus.NOT_MODIFIED) {
			log.trace("data has been modified");
			Feed feed = response.getBody();
			Date lastUpdateInFeed = null;
			for (Entry entry : feed.getEntries()) {
				if ((lastModified == null) || (entry.getUpdated().after(lastModified))) {
					Invoice order = restTemplate.getForEntity(entry.getAlternateLinks().get(0).getHref(), Invoice.class)
							.getBody();
					log.trace("saving order {}", order.getId());
					if ((lastUpdateInFeed == null) || (entry.getUpdated().after(lastUpdateInFeed))) {
						lastUpdateInFeed = entry.getUpdated();
					}
					orderRepository.save(order);
				}
			}
			if (response.getHeaders().getFirst("Last-Modified") != null) {
				lastModified = DateUtils.parseDate(response.getHeaders().getFirst("Last-Modified"));
				log.trace("Last-Modified header {}", lastModified);
			} else {
				if (lastUpdateInFeed != null) {
					lastModified = lastUpdateInFeed;
					log.trace("Last update in feed {}", lastModified);
				}
			}
		} else {
			log.trace("no new data");
		}
	}

}
