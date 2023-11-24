package cs416.lambda.capstone
import io.grpc.ManagedChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit


class StubNode(host: String, port: Int) : Closeable {
    private val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
    private val stub = RaftServiceGrpc.newBlockingStub(channel)

    suspend fun requestVote(request: VoteRequest): VoteResponse {
        return stub.requestVote(voteRequest {

        })
    }

    suspend fun appendEntries(request: AppendEntriesRequest): AppendEntriesResponse {
        return stub.appendEntries(appendEntriesRequest {

        })
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}