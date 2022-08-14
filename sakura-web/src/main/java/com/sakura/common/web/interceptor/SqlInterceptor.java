package com.sakura.common.web.interceptor;

import com.sakura.common.web.properties.WebSecurityProperties;
import com.sakura.common.web.wrapper.SQLInjectionHttpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @auther YangFan
 * @Date 2021/3/8 16:08
 */
//@ConfigurationProperties(prefix = "security.sql")
public class SqlInterceptor extends HandlerInterceptorAdapter {

    private static final Logger log = LoggerFactory.getLogger(SqlInterceptor.class);

    @Autowired
    private WebSecurityProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        boolean enabled = properties.getSql().isEnabled();
        // 不启用或者已忽略的URL不拦截
        if (!enabled || isExcludeUrl(request.getServletPath())) {
            return true;
        }

        log.debug("SqlInterceptor start pre ...");

        String method = request.getMethod();

        //请求头参数拦截过滤，请求头一般放个token就行了，不会到mysql数据库层面，所以暂不做拦截
//        Enumeration<String> headerNames = request.getHeaderNames();
//        while(headerNames.hasMoreElements()){
//            String name = headerNames.nextElement();
//            if (legalHeader(name)) {
//                continue;
//            }
//            String header = request.getHeader(name);
//            //sql注入直接拦截
//            if (sqlValidate(response, header)) return false;
//        }

        //get请求和表单请求参数拦截
        Enumeration<String> names = request.getParameterNames();
        while(names.hasMoreElements()){
            String name = names.nextElement();
            String[] values = request.getParameterValues(name);
            for(String value: values){
                //sql注入直接拦截
                if (sqlValidate(response, value)) return false;
            }
        }

        //Payload请求参数拦截(讲人话就是使用了json传参，后台使用@RequestBody接收参数)
        if("POST".equals(method)){
            SQLInjectionHttpServletRequestWrapper wrapper = new SQLInjectionHttpServletRequestWrapper(request);
            String requestBody = wrapper.getRequestBodyParame();
            if(!StringUtils.isEmpty(requestBody)){
                //sql注入直接拦截
                if (sqlValidate(response, requestBody)) return false;
            }
        }

        //TODO
        return true;
    }

    private boolean legalHeader(String headerName) {
        if ("accept".equals(headerName) || "cache-control".equals(headerName) || "content-type".equals(headerName)
                || "host".equals(headerName) || "connection".equals(headerName) || "user-agent".equals(headerName)
                || "content-length".equals(headerName) || "accept-encoding".equals(headerName)) {
            return true;
        }
        return false;
    }

    private boolean sqlValidate(HttpServletResponse response, String requestBody) throws IOException {
        if(sqlInject(requestBody)){
            response.setContentType("application/json; charset=utf-8");
            String jsonStr = "{\"code\":591,\"message\":\"请求参数含有非法字符："+requestBody+"\",\"data\":\"null\"}";
            response.getWriter().write(jsonStr);
            response.setStatus(591);
            return true;
        }
        return false;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        super.afterCompletion(request, response, handler, ex);
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        super.afterConcurrentHandlingStarted(request, response, handler);
    }

    /**
     *
     * @Title: sqlInject
     * @Description: TODO SQL 注入正在表达式(sql 函数关键字过滤)
     * @param: @param value
     * @param: @return
     * @return: boolean
     * @throws
     */
    public boolean sqlInject(String value){
        if(value == null || "".equals(value)){
            return false;
        }
        /**
         * 预编译SQL过滤正则表达式
         */
        Pattern sqlPattern = Pattern.compile(
                " and |\\+\\|\\|\\+|\\+or\\+|\\+and\\+| exec | execute |insert |select |delete |update |count |drop |declare |sitename |net user |xp_cmdshell |like' |table | from | grant | group_concat |column_name |information_schema.columns |table_schema |union | where |order by| truncate |'|\\*|;|--|//|‘",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = sqlPattern.matcher(value);
        return matcher.find();
    }

    /**
     * 判断是否为忽略的URL
     *
     * @param url URL路径
     * @return true-忽略，false-过滤
     */
    private boolean isExcludeUrl(String url) {
        List<String> excludes = properties.getSql().getExcludes();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        return excludes.stream().map(pattern -> Pattern.compile("^" + pattern)).map(p -> p.matcher(url))
                .anyMatch(Matcher::find);
    }

}
