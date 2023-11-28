import React, {useEffect, useState} from 'react';
import {Button, Col, Form, Row} from "react-bootstrap";
import styled from "styled-components";
import {ButtonWrapper, RowWrapper} from "./SellComponent";
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
        e.preventDefault()
        console.log("ONSUBMIT HAS BEEN TRIGGERED!");
        console.log(JSON.stringify(buyRequest));
        //TODO: SEND REQUEST TO LEADER NODE
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
                <Button onClick={onSubmit} variant="primary"> Submit </Button>
            </ButtonWrapper>
        </BuyWrapper>
    );
};

export default BuyComponent;
