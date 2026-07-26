package com.management.managementapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;


import com.management.managementapi.integrations.supabase.SupabaseProperties;

@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
@EnableConfigurationProperties(SupabaseProperties.class)
@SpringBootApplication
public class ManagementApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ManagementApiApplication.class, args);
	}

}
