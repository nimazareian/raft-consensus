import logo from './logo.svg';
import './App.css';
import AccountComponent from "./components/AccountComponent";
import PortfolioComponent from "./components/PortfolioComponent";
import BuyComponent from "./components/BuyComponent";
import SellComponent from "./components/SellComponent";

import styled from "styled-components";
export const Container = styled.div`
  margin: 2em;
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  gap: 1.5em;
`

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
