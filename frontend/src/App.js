import logo from './logo.svg';
import './App.css';
import AccountComponent from "./components/AccountComponent";
import PortfolioComponent from "./components/PortfolioComponent";
import BuyComponent from "./components/BuyComponent";
import SellComponent from "./components/SellComponent";
import styled from "styled-components";
import { TradeClient } from "./proto/app/src/main/proto/trade_grpc_web_pb";
import { BuyRequest } from "./proto/app/src/main/proto/trade_pb";
export const Container = styled.div`
  margin: 2em;
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  gap: 1.5em;
`

const tradeClient = async(name) => {
    const EnvoyUrl = "https://localhost:8000"; //idk if this is the url of the envoy
    const client = new TradeClient(EnvoyUrl);
    // const request = new proto.request;
    request.setTickr();
    request.setAmt();
    const response = await client.buyStock(request, {});
    console.log(response);
}

function App() {
    return (
        <Container>
            <AccountComponent></AccountComponent>
            <PortfolioComponent></PortfolioComponent>
            <BuyComponent></BuyComponent>
            <SellComponent></SellComponent>
        </Container>
    );
}

export default App;
