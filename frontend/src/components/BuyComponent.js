import React, {useState} from 'react';
import Form from "react-bootstrap/Form";
import Button from "react-bootstrap/Button";
import styled from "styled-components";
import {ButtonWrapper, RowWrapper} from "./SellComponent";
import {getServerUrl, parseServerResponse} from "../util/util";
import {useUserContext} from "../context/UserContext";
import {useLeaderContext} from "../context/LeaderContext";

export const BuyWrapper = styled.div`
  padding: 1.5rem;
  border: 2px solid grey;
  border-radius: 15px;
  grid-column: span 2;
`

const BuyComponent = () => {
    const { currUser } = useUserContext()
    const {leader, setLeader} = useLeaderContext()

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
        const url = getServerUrl(leader)
        // for local testing using `npm start`
        // const url = 'http://localhost:6001/buy'
        // for when using inside docker container
        // const url = 'http://172.20.0.4:6000/buy'
        fetch(`${url}/buy`, {
            method: 'POST',
            body: JSON.stringify({
                "amount": buyRequest.amt,
                "stock": buyRequest.tickr,
                "name": currUser.email
            })
        })
            .then(resp => resp.json())
            .then(resp => {
                parseServerResponse(resp, setLeader, onSubmit)
            })
            .catch(e => console.error(e))
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
