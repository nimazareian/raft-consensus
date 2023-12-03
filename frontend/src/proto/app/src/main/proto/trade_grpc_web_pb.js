/**
 * @fileoverview gRPC-Web generated client stub for 
 * @enhanceable
 * @public
 */

// Code generated by protoc-gen-grpc-web. DO NOT EDIT.
// versions:
// 	protoc-gen-grpc-web v1.4.2
// 	protoc              v4.25.1
// source: app/src/main/proto/trade.proto


/* eslint-disable */
// @ts-nocheck



const grpc = {};
grpc.web = require('grpc-web');

const proto = require('./trade_pb.js');

/**
 * @param {string} hostname
 * @param {?Object} credentials
 * @param {?grpc.web.ClientOptions} options
 * @constructor
 * @struct
 * @final
 */
proto.TradeClient =
    function(hostname, credentials, options) {
  if (!options) options = {};
  options.format = 'text';

  /**
   * @private @const {!grpc.web.GrpcWebClientBase} The client
   */
  this.client_ = new grpc.web.GrpcWebClientBase(options);

  /**
   * @private @const {string} The hostname
   */
  this.hostname_ = hostname.replace(/\/+$/, '');

};


/**
 * @param {string} hostname
 * @param {?Object} credentials
 * @param {?grpc.web.ClientOptions} options
 * @constructor
 * @struct
 * @final
 */
proto.TradePromiseClient =
    function(hostname, credentials, options) {
  if (!options) options = {};
  options.format = 'text';

  /**
   * @private @const {!grpc.web.GrpcWebClientBase} The client
   */
  this.client_ = new grpc.web.GrpcWebClientBase(options);

  /**
   * @private @const {string} The hostname
   */
  this.hostname_ = hostname.replace(/\/+$/, '');

};


/**
 * @const
 * @type {!grpc.web.MethodDescriptor<
 *   !proto.BuyRequest,
 *   !proto.BuyReply>}
 */
const methodDescriptor_Trade_BuyStock = new grpc.web.MethodDescriptor(
  '/Trade/BuyStock',
  grpc.web.MethodType.UNARY,
  proto.BuyRequest,
  proto.BuyReply,
  /**
   * @param {!proto.BuyRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.BuyReply.deserializeBinary
);


/**
 * @param {!proto.BuyRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @param {function(?grpc.web.RpcError, ?proto.BuyReply)}
 *     callback The callback function(error, response)
 * @return {!grpc.web.ClientReadableStream<!proto.BuyReply>|undefined}
 *     The XHR Node Readable Stream
 */
proto.TradeClient.prototype.buyStock =
    function(request, metadata, callback) {
  return this.client_.rpcCall(this.hostname_ +
      '/Trade/BuyStock',
      request,
      metadata || {},
      methodDescriptor_Trade_BuyStock,
      callback);
};


/**
 * @param {!proto.BuyRequest} request The
 *     request proto
 * @param {?Object<string, string>=} metadata User defined
 *     call metadata
 * @return {!Promise<!proto.BuyReply>}
 *     Promise that resolves to the response
 */
proto.TradePromiseClient.prototype.buyStock =
    function(request, metadata) {
  return this.client_.unaryCall(this.hostname_ +
      '/Trade/BuyStock',
      request,
      metadata || {},
      methodDescriptor_Trade_BuyStock);
};


/**
 * @const
 * @type {!grpc.web.MethodDescriptor<
 *   !proto.SellRequest,
 *   !proto.SellReply>}
 */
const methodDescriptor_Trade_SellStock = new grpc.web.MethodDescriptor(
  '/Trade/SellStock',
  grpc.web.MethodType.UNARY,
  proto.SellRequest,
  proto.SellReply,
  /**
   * @param {!proto.SellRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.SellReply.deserializeBinary
);


/**
 * @param {!proto.SellRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @param {function(?grpc.web.RpcError, ?proto.SellReply)}
 *     callback The callback function(error, response)
 * @return {!grpc.web.ClientReadableStream<!proto.SellReply>|undefined}
 *     The XHR Node Readable Stream
 */
proto.TradeClient.prototype.sellStock =
    function(request, metadata, callback) {
  return this.client_.rpcCall(this.hostname_ +
      '/Trade/SellStock',
      request,
      metadata || {},
      methodDescriptor_Trade_SellStock,
      callback);
};


/**
 * @param {!proto.SellRequest} request The
 *     request proto
 * @param {?Object<string, string>=} metadata User defined
 *     call metadata
 * @return {!Promise<!proto.SellReply>}
 *     Promise that resolves to the response
 */
proto.TradePromiseClient.prototype.sellStock =
    function(request, metadata) {
  return this.client_.unaryCall(this.hostname_ +
      '/Trade/SellStock',
      request,
      metadata || {},
      methodDescriptor_Trade_SellStock);
};


module.exports = proto;

