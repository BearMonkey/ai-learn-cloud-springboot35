package org.monkey.iam.api.feign;

import org.monkey.iam.api.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * IAM 服务 Feign 客户端接口
 *
 * <p>供其他微服务声明式调用 iam-service 的用户查询接口。
 * 路径 "/iam" 与网关 StripPrefix 后的路由保持一致
 *
 * <p>使用示例：
 * <pre>{@code
 * @Autowired
 * private IamFeignClient iamFeignClient;
 *
 * UserDTO user = iamFeignClient.getUserById(1L);
 * }</pre>
 *
 * @author monkey
 */
@FeignClient(name = "iam-service", path = "/iam")
public interface IamFeignClient {

    /**
     * 根据用户ID查询用户信息
     *
     * @param id 用户ID
     * @return 用户信息 DTO
     */
    @GetMapping("/users/{id}")
    UserDTO getUserById(@PathVariable("id") Long id);
}
