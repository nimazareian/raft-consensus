import config from "../resources/config/default.json";

export const getServerUrl = ({address, port}) => {
    if (address == null) {
        const server = config.hosts[0]
        return `http://${server.address}:${server.port}`
    } else {
        return `http://${address}:${port}`
    }
}

export const parseServerResponse = (resp, setLeader, reSubmit) => {
    if (resp.success || resp.sold || resp.purchased) {
        console.log('success!')
    } else {
        const leader = resp.serverResponse
        setLeader(leader.leaderAddress, leader.leaderPort)
        reSubmit()
    }
}