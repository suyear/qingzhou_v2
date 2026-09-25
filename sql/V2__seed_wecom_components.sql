-- =============================================================================
-- 预置企业微信 6 个核心组件
-- URL 中 ${access_token} 由 TokenManager 在运行时替换
-- Schema 供画布节点卡片展示入参/出参预览与必填标签
-- =============================================================================

USE `qingzhou`;

INSERT INTO `qz_api_component` (
  `component_code`, `component_name`, `provider`, `category`,
  `http_method`, `url_template`,
  `headers_schema`, `query_schema`, `body_schema`, `response_schema`,
  `timeout_ms`, `retry_times`, `retry_interval_ms`,
  `is_preset`, `status`, `description`, `extra_config`
) VALUES
-- 1. 发送应用消息
(
  'wecom.send_message',
  '发送应用消息',
  'WECOM',
  'MESSAGE',
  'POST',
  'https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=${access_token}',
  JSON_OBJECT('type', 'object', 'properties', JSON_OBJECT('Content-Type', JSON_OBJECT('type', 'string', 'const', 'application/json'))),
  JSON_OBJECT('type', 'object', 'properties', JSON_OBJECT()),
  JSON_OBJECT(
    'type', 'object',
    'required', JSON_ARRAY('touser', 'msgtype', 'agentid'),
    'properties', JSON_OBJECT(
      'touser', JSON_OBJECT('type', 'string', 'description', '接收成员UserID，多人用|分隔，@all表示全部'),
      'toparty', JSON_OBJECT('type', 'string', 'description', '接收部门ID，多个用|分隔'),
      'totag', JSON_OBJECT('type', 'string', 'description', '接收标签ID，多个用|分隔'),
      'msgtype', JSON_OBJECT('type', 'string', 'enum', JSON_ARRAY('text', 'markdown', 'textcard', 'template_card')),
      'agentid', JSON_OBJECT('type', 'integer', 'description', '企业应用ID'),
      'text', JSON_OBJECT('type', 'object', 'properties', JSON_OBJECT('content', JSON_OBJECT('type', 'string', 'description', '消息内容'))),
      'safe', JSON_OBJECT('type', 'integer', 'description', '是否保密消息 0否 1是')
    )
  ),
  JSON_OBJECT(
    'type', 'object',
    'properties', JSON_OBJECT(
      'errcode', JSON_OBJECT('type', 'integer'),
      'errmsg', JSON_OBJECT('type', 'string'),
      'msgid', JSON_OBJECT('type', 'string'),
      'invaliduser', JSON_OBJECT('type', 'string')
    )
  ),
  10000, 2, 1000, 1, 1,
  '向企业成员发送应用消息（文本/Markdown/卡片）',
  JSON_OBJECT('needAccessToken', TRUE)
),
-- 2. 获取部门列表
(
  'wecom.get_department_list',
  '获取部门列表',
  'WECOM',
  'ORG',
  'GET',
  'https://qyapi.weixin.qq.com/cgi-bin/department/list?access_token=${access_token}',
  NULL,
  JSON_OBJECT(
    'type', 'object',
    'properties', JSON_OBJECT(
      'id', JSON_OBJECT('type', 'integer', 'description', '部门ID，不填则拉取全量组织架构')
    )
  ),
  NULL,
  JSON_OBJECT(
    'type', 'object',
    'properties', JSON_OBJECT(
      'errcode', JSON_OBJECT('type', 'integer'),
      'errmsg', JSON_OBJECT('type', 'string'),
      'department', JSON_OBJECT('type', 'array', 'items', JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(
          'id', JSON_OBJECT('type', 'integer'),
          'name', JSON_OBJECT('type', 'string'),
          'parentid', JSON_OBJECT('type', 'integer'),
          'order', JSON_OBJECT('type', 'integer')
        )
      ))
    )
  ),
  10000, 1, 1000, 1, 1,
  '获取企业部门列表，可用于组织架构同步',
  JSON_OBJECT('needAccessToken', TRUE)
),
-- 3. 获取部门成员
(
  'wecom.get_user_simplelist',
  '获取部门成员',
  'WECOM',
  'ORG',
  'GET',
  'https://qyapi.weixin.qq.com/cgi-bin/user/simplelist?access_token=${access_token}',
  NULL,
  JSON_OBJECT(
    'type', 'object',
    'required', JSON_ARRAY('department_id'),
    'properties', JSON_OBJECT(
      'department_id', JSON_OBJECT('type', 'integer', 'description', '部门ID'),
      'fetch_child', JSON_OBJECT('type', 'integer', 'description', '是否递归获取子部门成员 0否 1是')
    )
  ),
  NULL,
  JSON_OBJECT(
    'type', 'object',
    'properties', JSON_OBJECT(
      'errcode', JSON_OBJECT('type', 'integer'),
      'errmsg', JSON_OBJECT('type', 'string'),
      'userlist', JSON_OBJECT('type', 'array', 'items', JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(
          'userid', JSON_OBJECT('type', 'string'),
          'name', JSON_OBJECT('type', 'string'),
          'department', JSON_OBJECT('type', 'array', 'items', JSON_OBJECT('type', 'integer'))
        )
      ))
    )
  ),
  10000, 1, 1000, 1, 1,
  '获取指定部门下的成员简要信息',
  JSON_OBJECT('needAccessToken', TRUE)
),
-- 4. 获取成员详情
(
  'wecom.get_user',
  '获取成员详情',
  'WECOM',
  'ORG',
  'GET',
  'https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=${access_token}',
  NULL,
  JSON_OBJECT(
    'type', 'object',
    'required', JSON_ARRAY('userid'),
    'properties', JSON_OBJECT(
      'userid', JSON_OBJECT('type', 'string', 'description', '成员UserID')
    )
  ),
  NULL,
  JSON_OBJECT(
    'type', 'object',
    'properties', JSON_OBJECT(
      'errcode', JSON_OBJECT('type', 'integer'),
      'errmsg', JSON_OBJECT('type', 'string'),
      'userid', JSON_OBJECT('type', 'string'),
      'name', JSON_OBJECT('type', 'string'),
      'department', JSON_OBJECT('type', 'array'),
      'mobile', JSON_OBJECT('type', 'string'),
      'email', JSON_OBJECT('type', 'string'),
      'status', JSON_OBJECT('type', 'integer')
    )
  ),
  8000, 1, 1000, 1, 1,
  '根据 UserID 获取成员详细资料',
  JSON_OBJECT('needAccessToken', TRUE)
),
-- 5. 创建群聊
(
  'wecom.create_appchat',
  '创建群聊',
  'WECOM',
  'GROUP',
  'POST',
  'https://qyapi.weixin.qq.com/cgi-bin/appchat/create?access_token=${access_token}',
  JSON_OBJECT('type', 'object', 'properties', JSON_OBJECT('Content-Type', JSON_OBJECT('type', 'string', 'const', 'application/json'))),
  NULL,
  JSON_OBJECT(
    'type', 'object',
    'required', JSON_ARRAY('userlist'),
    'properties', JSON_OBJECT(
      'name', JSON_OBJECT('type', 'string', 'description', '群聊名称'),
      'owner', JSON_OBJECT('type', 'string', 'description', '群主UserID'),
      'userlist', JSON_OBJECT('type', 'array', 'minItems', 2, 'items', JSON_OBJECT('type', 'string'), 'description', '成员UserID列表，至少2人'),
      'chatid', JSON_OBJECT('type', 'string', 'description', '自定义群ID，不填则系统生成')
    )
  ),
  JSON_OBJECT(
    'type', 'object',
    'properties', JSON_OBJECT(
      'errcode', JSON_OBJECT('type', 'integer'),
      'errmsg', JSON_OBJECT('type', 'string'),
      'chatid', JSON_OBJECT('type', 'string')
    )
  ),
  10000, 1, 1000, 1, 1,
  '创建企业微信群聊会话，返回 chatid 可供后续发消息',
  JSON_OBJECT('needAccessToken', TRUE)
),
-- 6. 发送群聊消息
(
  'wecom.send_appchat',
  '发送群聊消息',
  'WECOM',
  'GROUP',
  'POST',
  'https://qyapi.weixin.qq.com/cgi-bin/appchat/send?access_token=${access_token}',
  JSON_OBJECT('type', 'object', 'properties', JSON_OBJECT('Content-Type', JSON_OBJECT('type', 'string', 'const', 'application/json'))),
  NULL,
  JSON_OBJECT(
    'type', 'object',
    'required', JSON_ARRAY('chatid', 'msgtype'),
    'properties', JSON_OBJECT(
      'chatid', JSON_OBJECT('type', 'string', 'description', '群聊ID'),
      'msgtype', JSON_OBJECT('type', 'string', 'enum', JSON_ARRAY('text', 'markdown', 'textcard')),
      'text', JSON_OBJECT('type', 'object', 'properties', JSON_OBJECT('content', JSON_OBJECT('type', 'string'))),
      'safe', JSON_OBJECT('type', 'integer', 'description', '是否保密 0否 1是')
    )
  ),
  JSON_OBJECT(
    'type', 'object',
    'properties', JSON_OBJECT(
      'errcode', JSON_OBJECT('type', 'integer'),
      'errmsg', JSON_OBJECT('type', 'string')
    )
  ),
  10000, 2, 1000, 1, 1,
  '向已创建的企业微信群聊发送消息',
  JSON_OBJECT('needAccessToken', TRUE)
);
