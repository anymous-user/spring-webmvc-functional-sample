package com.example.demo;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.servlet.ServletException;
import java.io.IOException;
import java.net.URI;
import java.util.stream.Stream;

import static javax.persistence.GenerationType.AUTO;
import static org.springframework.web.servlet.function.RequestPredicates.*;
import static org.springframework.web.servlet.function.RouterFunctions.route;
import static org.springframework.web.servlet.function.ServerResponse.*;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    public RouterFunction<ServerResponse> routes(PostHandler postHandler, BlogProperties blogProperties) {
        return route(GET("/info"), (req) -> ok().body(blogProperties))
                .andRoute(GET("/posts"), postHandler::all)
                .andRoute(POST("/posts"), postHandler::create)
                .andRoute(GET("/posts/{id}"), postHandler::get)
                .andRoute(PUT("/posts/{id}"), postHandler::update)
                .andRoute(DELETE("/posts/{id}"), postHandler::delete);
    }
}

@ConfigurationProperties(prefix = "blog")
@Data
class BlogProperties {
    private String title = "Nobody's Blog";
    private String description = "Description of Nobody's Blog";
    private String author = "Nobody";
}

@Component
class PostHandler {

    private final PostRepository posts;

    public PostHandler(PostRepository posts) {
        this.posts = posts;
    }

    public ServerResponse all(ServerRequest req) {
        return ok().body(this.posts.findAll());
    }

    public ServerResponse create(ServerRequest req) throws ServletException, IOException {

        var saved = this.posts.save(req.body(Post.class));
        return created(URI.create("/posts/" + saved.getId())).build();
    }

    public ServerResponse get(ServerRequest req) {
        return this.posts.findById(Long.valueOf(req.pathVariable("id")))
                .map(post -> ok().body(post))
                .orElse(notFound().build());
    }

    public ServerResponse update(ServerRequest req) throws ServletException, IOException {
        var data = req.body(Post.class);

        return this.posts.findById(Long.valueOf(req.pathVariable("id")))
                .map(
                        post -> {
                            post.setTitle(data.getTitle());
                            post.setContent(data.getContent());
                            return post;
                        }
                )
                .map(post -> this.posts.save(post))
                .map(post -> noContent().build())
                .orElse(notFound().build());

    }

    public ServerResponse delete(ServerRequest req) {
        return this.posts.findById(Long.valueOf(req.pathVariable("id")))
                .map(
                        post -> {
                            this.posts.delete(post);
                            return noContent().build();
                        }
                )
                .orElse(notFound().build());
    }

}

@Component
@Slf4j
@RequiredArgsConstructor
class DataInitializer {

    private final PostRepository posts;

    @EventListener(ApplicationReadyEvent.class)
    public void initPosts() {
        log.info(" start data initializing...");
        this.posts.deleteAll();
        Stream.of("Post one", "Post two").forEach(
                title -> this.posts.save(Post.builder().title(title).content("content of " + title).build())
        );
        log.info(" done data initialization...");
        log.info(" initialized data::");
        this.posts.findAll().forEach(p -> log.info(p.toString()));
    }

}

interface PostRepository extends JpaRepository<Post, Long> {
}

@Data
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
class Post {

    @Id
    @GeneratedValue(strategy = AUTO)
    private Long id;
    private String title;
    private String content;

}