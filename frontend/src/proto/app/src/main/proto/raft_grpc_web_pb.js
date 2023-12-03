/**
 * @fileoverview gRPC-Web generated client stub for 
 * @enhanceable
 * @public
 */

// Code generated by protoc-gen-grpc-web. DO NOT EDIT.
// versions:
// 	protoc-gen-grpc-web v1.4.2
// 	protoc              v4.25.1
// source: app/src/main/proto/raft.proto


/* eslint-disable */
// @ts-nocheck



const grpc = {};
grpc.web = require('grpc-web');


var trade_pb = require('../../../../trade_pb.js')
const proto = require('./raft_pb.js');

/**
 * @param {string} hostname
 * @param {?Object} credentials
 * @param {?grpc.web.ClientOptions} options
 * @constructor
 * @struct
 * @final
 */
proto.RaftServiceClient =
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
proto.RaftServicePromiseClient =
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
 *   !proto.AppendEntriesRequest,
 *   !proto.AppendEntriesResponse>}
 */
const methodDescriptor_RaftService_AppendEntries = new grpc.web.MethodDescriptor(
  '/RaftService/AppendEntries',
  grpc.web.MethodType.UNARY,
  proto.AppendEntriesRequest,
  proto.AppendEntriesResponse,
  /**
   * @param {!proto.AppendEntriesRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.AppendEntriesResponse.deserializeBinary
);


/**
 * @param {!proto.AppendEntriesRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @param {function(?grpc.web.RpcError, ?proto.AppendEntriesResponse)}
 *     callback The callback function(error, response)
 * @return {!grpc.web.ClientReadableStream<!proto.AppendEntriesResponse>|undefined}
 *     The XHR Node Readable Stream
 */
proto.RaftServiceClient.prototype.appendEntries =
    function(request, metadata, callback) {
  return this.client_.rpcCall(this.hostname_ +
      '/RaftService/AppendEntries',
      request,
      metadata || {},
      methodDescriptor_RaftService_AppendEntries,
      callback);
};


/**
 * @param {!proto.AppendEntriesRequest} request The
 *     request proto
 * @param {?Object<string, string>=} metadata User defined
 *     call metadata
 * @return {!Promise<!proto.AppendEntriesResponse>}
 *     Promise that resolves to the response
 */
proto.RaftServicePromiseClient.prototype.appendEntries =
    function(request, metadata) {
  return this.client_.unaryCall(this.hostname_ +
      '/RaftService/AppendEntries',
      request,
      metadata || {},
      methodDescriptor_RaftService_AppendEntries);
};


/**
 * @const
 * @type {!grpc.web.MethodDescriptor<
 *   !proto.VoteRequest,
 *   !proto.VoteResponse>}
 */
const methodDescriptor_RaftService_RequestVote = new grpc.web.MethodDescriptor(
  '/RaftService/RequestVote',
  grpc.web.MethodType.UNARY,
  proto.VoteRequest,
  proto.VoteResponse,
  /**
   * @param {!proto.VoteRequest} request
   * @return {!Uint8Array}
   */
  function(request) {
    return request.serializeBinary();
  },
  proto.VoteResponse.deserializeBinary
);


/**
 * @param {!proto.VoteRequest} request The
 *     request proto
 * @param {?Object<string, string>} metadata User defined
 *     call metadata
 * @param {function(?grpc.web.RpcError, ?proto.VoteResponse)}
 *     callback The callback function(error, response)
 * @return {!grpc.web.ClientReadableStream<!proto.VoteResponse>|undefined}
 *     The XHR Node Readable Stream
 */
proto.RaftServiceClient.prototype.requestVote =
    function(request, metadata, callback) {
  return this.client_.rpcCall(this.hostname_ +
      '/RaftService/RequestVote',
      request,
      metadata || {},
      methodDescriptor_RaftService_RequestVote,
      callback);
};


/**
 * @param {!proto.VoteRequest} request The
 *     request proto
 * @param {?Object<string, string>=} metadata User defined
 *     call metadata
 * @return {!Promise<!proto.VoteResponse>}
 *     Promise that resolves to the response
 */
proto.RaftServicePromiseClient.prototype.requestVote =
    function(request, metadata) {
  return this.client_.unaryCall(this.hostname_ +
      '/RaftService/RequestVote',
      request,
      metadata || {},
      methodDescriptor_RaftService_RequestVote);
};


module.exports = proto;

