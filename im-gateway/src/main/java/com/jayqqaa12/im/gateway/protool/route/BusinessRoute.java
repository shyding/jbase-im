package com.jayqqaa12.im.gateway.protool.route;

import cn.hutool.json.JSONObject;
import com.jayqqaa12.im.business.support.BusinessDispatcher;
import com.jayqqaa12.im.common.model.consts.Req;
import com.jayqqaa12.im.common.model.vo.RpcDTO;
import com.jayqqaa12.im.gateway.protool.base.Route;
import com.jayqqaa12.im.gateway.protool.base.Router;
import com.jayqqaa12.im.gateway.protool.base.TcpContext;
import com.jayqqaa12.im.gateway.protool.model.vo.TcpReqVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author: 12
 * @create: 2019-12-26 13:23
 **/
@Route(req = Req.BUSINESS)
public class BusinessRoute implements Router<JSONObject> {

  @Autowired
  BusinessDispatcher dispatcher;


  @Override
  public void handle(TcpContext context, TcpReqVO req, JSONObject data) throws Exception {

    RpcDTO rpc = new RpcDTO();
    BeanUtils.copyProperties(context, rpc);
    BeanUtils.copyProperties(req, rpc);

    //调用业务层代码 暂时先直接调用 后面改成rpc 调用
    context.response(req, dispatcher.handler(rpc));

  }
}
