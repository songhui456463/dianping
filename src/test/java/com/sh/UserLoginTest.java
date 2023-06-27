package com.sh;

import com.sh.config.BeanConfig;
import com.sh.dto.LoginFormDTO;
import com.sh.dto.Result;
import com.sh.entry.Environment;
import com.sh.entry.User;
import com.sh.mapper.UserMapper;
import com.sh.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class UserLoginTest {

    @Resource
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IUserService userService;

    @Test
    void login() throws Exception {
        // 获取所有用户
        List<User> users = userMapper.selectList(null);
        // 将所有token存入txt
        List<String> tokens = new ArrayList<>();
        // 登录
        users.forEach(item -> {
            // 发送验证码
            Result result = userService.sendCode(item.getPhone(), null);
            String code = (String) result.getData();
            // 登录信息
            LoginFormDTO loginFormDTO =  new LoginFormDTO();
            loginFormDTO.setCode(code);
            loginFormDTO.setPhone(item.getPhone());
            // 验证登录信息
            Result result1 = userService.login(loginFormDTO, null);
            String token = (String) result1.getData();
            // 存入当集合中
            tokens.add(token);

        });
        writeFileContext(tokens, "./tokens.txt");
    }

    /**
     * 将list按行写入到txt文件中
     *
     * @param strings
     * @param path
     * @throws Exception
     */
    public void writeFileContext(List<String> strings, String path) throws Exception {
        File file = new File(path);
        //如果没有文件就创建
        if (!file.isFile()) {
            file.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(path));
        for (String l : strings) {
            writer.write(l + "\r\n");
        }
        writer.close();
    }


    @Test
    void test() {
        AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext(BeanConfig.class);
        String osName = ac.getEnvironment().getProperty("os.name");
        java.lang.System.out.println(osName);
        Map<String, Environment> beans = ac.getBeansOfType(Environment.class);
        java.lang.System.out.println(beans);
    }


}
