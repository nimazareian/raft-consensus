import React, {useState} from 'react';
import Button from 'react-bootstrap/Button';
import styled from "styled-components";
import Form from "react-bootstrap/Form";
import {useUserContext} from "../context/UserContext";
import {useLeaderContext} from "../context/LeaderContext";
import {getServerUrl, parseServerResponse} from "../util/util";

const AccountWrapper = styled.div`

  padding: 1.5rem;
  border: 2px solid grey;
  border-radius: 15px;
  grid-column: span 2;
`
const AccountComponent = () => {
    const [accountBalance, setAccountBalance] = useState(0);
    const [depositAmount, setDepositAmount] = useState(0);

    const {currUser} = useUserContext()
    const {leader, setLeader} = useLeaderContext()
    const updateBalance = () => {
        const url = getServerUrl(leader)
        console.log(`sending request to ${url} with name ${currUser} and amount ${depositAmount}`)
        fetch(`${url}/deposit`, {
            method: 'POST',
            body: JSON.stringify({
                "name": "wow",
                "amount": 4,
            })
        })
            .then(resp => resp.json())
            .then(resp => {
                parseServerResponse(resp, setLeader, updateBalance)
            })
            .catch(e => console.error(e))
        setAccountBalance(accountBalance + depositAmount);
    }

    const updateDepositAmount = (e) => {
        e.preventDefault();
        setDepositAmount(parseInt(e.target.value));
    }

    return (
        <AccountWrapper>
            <h1>Account Balance: {accountBalance}</h1>
            <Form.Label>Enter Amount to Deposit:</Form.Label>
            <Form.Control onChange={updateDepositAmount} name="amt" type="number" required/>
            <Button size="lg" onClick={updateBalance} variant="primary"> Add Funds </Button>
        </AccountWrapper>
    );
};

export default AccountComponent;
