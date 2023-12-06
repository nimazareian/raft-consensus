import React, {useState} from 'react';
import { Row, Form, Button} from "react-bootstrap";
import styled from "styled-components";
import {useUserContext} from "../context/UserContext";
import {useLeaderContext} from "../context/LeaderContext";
import {getServerUrl, parseServerResponse} from "../util/util";

export const SellWrapper = styled.div`
  //background: blue;
  padding: 1.5rem;
  border: 2px solid grey;
  border-radius: 15px;
  grid-column: span 2;
`

export const RowWrapper = styled(Row)`
  width: 100%;
  margin-top: 1em;
  margin-bottom: 2em;
  padding-right: 0;
  padding-left: 0;
  margin-left: 0.05em;
`

export const ButtonWrapper = styled.div`
  //width: 100%;
  display: flex;
  justify-content: center;
`



const SellComponent = () => {
    const {currUser} = useUserContext()
    const {leader, setLeader} = useLeaderContext()


    const [sellRequest, setSellRequest] = useState({
        tickr: '',
        amt: ''
    });

    const updateField = (e) => {
        const {value, name} = e.target;
        setSellRequest({
            ...sellRequest,
            [name]: value
        });
    }
    const onSubmit = (e) => {
        const url = getServerUrl(leader)
        console.log(`sending request to ${url}`)
        fetch(`${url}/sell`, {
            method: 'POST',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                "name": currUser.email,
                "stock": sellRequest.tickr,
                "amount": parseInt(sellRequest.amt, 10),
            })
        })
            .then(resp => resp.json())
            .then(resp => {
                parseServerResponse(resp, setLeader, onSubmit)
            })
            .catch(e => console.error(e))
    }

    return (
        <SellWrapper>
            <Form>
                <h1>Sell:</h1>
                <RowWrapper>
                    <Form.Label>Select the Stock:</Form.Label>
                    <Form.Control onChange={updateField} name="tickr" type="text" required/>
                </RowWrapper>
                <RowWrapper>
                    <Form.Label>Enter the Number of Stocks to Sell:</Form.Label>
                    <Form.Control onChange={updateField} name="amt" type="number" required/>
                </RowWrapper>
                <ButtonWrapper>
                    <Button size="lg" onClick={onSubmit} variant="primary"> Submit </Button>
                </ButtonWrapper>
            </Form>
        </SellWrapper>
    );
};

export default SellComponent;
