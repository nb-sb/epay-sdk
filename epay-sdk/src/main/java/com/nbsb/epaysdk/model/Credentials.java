package com.nbsb.epaysdk.model;

/** 一个配置只能携带一种完整凭证，类型系统禁止 MD5/RSA 混装。 */
public sealed interface Credentials permits Md5Credentials, RsaCredentials {}
