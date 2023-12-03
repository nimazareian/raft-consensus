import React, {useState} from 'react';
import Form from "react-bootstrap/Form";
import Button from "react-bootstrap/Button";
import styled from "styled-components";
import {ButtonWrapper, RowWrapper} from "./SellComponent";
import {TradeContext} from "../index";
import {useContext} from "react";
export const BuyWrapper = styled.div`
  padding: 1.5rem;
  border: 2px solid grey;
  border-radius: 15px;
  grid-column: span 2;
`

const BuyComponent = () => {
    const [buyRequest, setBuyRequest] = useState({
        tickr: '',
        amt: ''

    });
    // const [subTotal, setSubTotal] = useState(0);

    const updateField = (e) => {
        const {value, name} = e.target;
        setBuyRequest({
            ...buyRequest,
            [name]: value
        });
    }


    const onSubmit = (e) => {
        e.preventDefault();
        console.log("ONSUBMIT HAS BEEN TRIGGERED!");
        console.log(JSON.stringify(buyRequest));
        console.log("========BEFORE GRPC IS CALLED===========");
        const EnvoyUrl = "https://localhost:8000"; //idk if this is the url of the envoy
        const request = proto.BuyRequest;
        // const client = proto.TradeClient(EnvoyUrl, null, {}).buyStock(request, {});
        request.setStock('GME');
        request.setAmount(500);
        // const response = await client.buyStock(request, {});
        console.log(response);
        console.log("========AFTER GRPC IS CALLED============");
    }

    return (
        <BuyWrapper>
            <h1>Buy:</h1>
            <RowWrapper>
                <Form.Label>Select the Stock:</Form.Label>
                <Form.Control onChange={updateField} name="tickr" type="text" required/>
            </RowWrapper>
            <RowWrapper>
                <Form.Label>Enter the Number of Stocks to Buy:</Form.Label>
                <Form.Control onChange={updateField} name="amt" type="number" required/>
            </RowWrapper>
            {/*DEPENDING ON WHERE WE GO WITH THIS REMOVE SUBTOTAL?*/}
            {/*<h2>Subtotal: {subTotal} </h2> */}
            <ButtonWrapper>
                <Button onClick={onSubmit} variant="primary" size="lg"> Submit </Button>
            </ButtonWrapper>
        </BuyWrapper>
    );
};

export default BuyComponent;
