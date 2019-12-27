package com.jayqqaa12.im.gateway.protool.base;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.jayqqaa12.im.common.model.consts.Req;
import com.jayqqaa12.im.common.model.consts.Resp;
import com.jayqqaa12.im.common.model.consts.VersionEnum;
import com.jayqqaa12.im.common.model.vo.TcpReqVO;
import com.jayqqaa12.im.common.util.ValidatorKit;
import com.jayqqaa12.jbase.spring.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

@Slf4j
public class RouterChain {

  private static Multimap<Integer, RouterVo> routerMap = ArrayListMultimap.create();


  public static void init(ApplicationContext context) {
    // load router
    for (String beanName : context.getBeanNamesForAnnotation(Route.class)) {
      Router bean = (Router) context.getBean(beanName);

      Route route = bean.getClass().getAnnotation(Route.class);
      if (route == null) throw new RuntimeException("Router must use @Route annotation");

      routerMap.put(route.req(), new  RouterVo(bean, route));

    }
  }


  static void run(TcpContext context, TcpReqVO request) {
    try {
      executor(request, context);
    } catch (IllegalArgumentException e) {
      context.error(Resp.PARAM_ERROR, "参数异常 :" + e.getMessage());
    } catch (BusinessException e) {
      context.responseError(request, e.getCode(), e.getMessage());
    } catch (Exception e) {

      log.error("执行 route {} 异常 {}", request.getCode(), e);
      error(request, context, e);
    }
  }

  /**
   * 直接执行
   *
   * @param req
   * @param context
   */
  static void exec(TcpReqVO req, TcpContext context) throws Exception {
    RouterVo router = getRouter(req);
    if (router == null) context.error(Resp.CMD_ERROR, "指令不存在");
    else {
      Type type = ((ParameterizedType) (router.getRouter().getClass().getGenericInterfaces())[0]).getActualTypeArguments()[0];

      if (type.getTypeName().equals("java.lang.Object"))
        router.getRouter().handle(context, req, req.getData());
      else {
        Object obj = req.getData();

        if (obj instanceof JSONObject) {
          obj = ((JSONObject) obj).toJavaObject(type);
        } else if (obj instanceof JSONArray) {
          obj = ((JSONArray) obj).toJavaObject(type);
        }

        ValidatorKit.validate(obj);
        router.getRouter().handle(context, req, obj);
      }
    }
  }

  private static RouterVo getRouter(TcpReqVO req) {

    int code = req.getCode();

    //如果是业务指令直接定位到业务router
    if (code > Req.BUSINESS) code = Req.BUSINESS;

    Collection<RouterVo> routerVoList = routerMap.get(code);
    //根据版本找到合适的router

    for (RouterVo routerVo : routerVoList) {
      VersionEnum versionEnum = VersionEnum.of(req.getVersion());
      if (versionEnum.getVersion() >= routerVo.getRoute().min().getVersion()
        && versionEnum.getVersion() <= routerVo.getRoute().max().getVersion()) {
        return routerVo;
      }
    }
    return null;
  }


  private static void checkLogin(TcpReqVO req, TcpContext tcpContext) {
    if (Req.HEART == req.getCode()) return;

    RouterVo route = getRouter(req);
    if (route != null && !route.getRoute().checkLogin()) return;

    if (tcpContext.getUserOrDevice() == null) {
      tcpContext.responseError(req, Resp.UNLOGIN, "请先登录");
    }

  }

  private static void executor(TcpReqVO req, TcpContext context) throws Exception {
    if (req == null) return;
    if (Req.HEART == req.getCode()) return;

    checkParam(req);
    checkLogin(req, context);

    if (!context.isRepectResp(req)) {
      exec(req, context);
    }

  }


  private static void checkParam(TcpReqVO context) {
    Assert.notNull(context, "req can't null");
    Assert.notNull(context.getCode(), "code can't null");
    Assert.notNull(context.getUuid(), "uuid can't null");
    Assert.notNull(context.getTimestamp(), "timestamp can't null");
    Assert.notNull(context.getVersion(), "version can't null");
  }


  private static void error(TcpReqVO request, TcpContext context, Throwable throwable) {
    if (context != null) {
      context.responseError(request, Resp.ERROR, "异常 : " + throwable.getMessage());
    } else {
      context.error("unkonwn error " + throwable.getMessage());
    }
  }


  @Data
  @AllArgsConstructor
  private static class RouterVo {
    Router router;
    Route route;


  }


}
