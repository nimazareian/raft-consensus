package cs416.lambda.capstone.config

data class ClusterConfig(val id: String, val cluster: ArrayList<NodeConfig>)

data class NodeConfig(val id: Int, val port: Int, val address: String)